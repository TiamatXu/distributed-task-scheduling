package com.example.distributedtask;

import com.example.distributedtask.task.Task;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class TaskProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TaskProcessor.class);
    private static final String TASK_QUEUE_KEY_PREFIX = "agent:tasks:";
    private static final String AGENT_ID = "agent-001"; // TODO: Replace with dynamic agent ID

    private final StringRedisTemplate stringRedisTemplate;
    private final ShellTaskExecutor shellTaskExecutor;
    private final ObjectMapper objectMapper;
    private final RedisTaskRepository redisTaskRepository;
    private final ExecutorService executorService;

    private volatile boolean running = true;

    public TaskProcessor(StringRedisTemplate stringRedisTemplate, ShellTaskExecutor shellTaskExecutor, ObjectMapper objectMapper, RedisTaskRepository redisTaskRepository) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shellTaskExecutor = shellTaskExecutor;
        this.objectMapper = objectMapper;
        this.redisTaskRepository = redisTaskRepository;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @PostConstruct
    public void init() {
        logger.info("TaskProcessor initialized. Starting task polling for agent: {}", AGENT_ID);
        executorService.submit(this::pollTasks);
    }

    private void pollTasks() {
        String taskQueueKey = TASK_QUEUE_KEY_PREFIX + AGENT_ID;
        logger.info("Polling for tasks on Redis list: {}", taskQueueKey);

        while (running) {
            try {
                String taskJson = stringRedisTemplate.opsForList().rightPop(taskQueueKey, Duration.ofSeconds(5));

                if (taskJson != null) {
                    logger.info("Received task JSON: {}", taskJson);
                    Task task = objectMapper.readValue(taskJson, Task.class);
                    task.setAgentId(AGENT_ID); // Set agent ID for the task
                    redisTaskRepository.save(task); // Save task initially

                    logger.info("Processing task: {}", task.getTaskId());

                    // Update status to RUNNING
                    redisTaskRepository.updateStatus(task.getTaskId(), Task.TaskStatus.RUNNING);
                    task.setStatus(Task.TaskStatus.RUNNING); // Update local object too
                    task.setStartTime(System.currentTimeMillis());

                    String executionResult;
                    Task.TaskStatus finalStatus;

                    if (task.getType() == Task.TaskType.SHELL) {
                        executionResult = shellTaskExecutor.execute(task.getPayload());
                        // Determine final status based on executionResult (simple check for now)
                        if (executionResult.startsWith("Command failed") || executionResult.contains("Error executing command")) {
                            finalStatus = Task.TaskStatus.FAILED;
                        } else {
                            finalStatus = Task.TaskStatus.SUCCESS;
                        }
                    } else {
                        logger.warn("Unsupported task type for task {}: {}", task.getTaskId(), task.getType());
                        executionResult = "Unsupported task type: " + task.getType();
                        finalStatus = Task.TaskStatus.FAILED;
                    }
                    task.setResult(executionResult);
                    task.setFinishTime(System.currentTimeMillis());
                    task.setStatus(finalStatus); // Update local object too

                    // Update final status and result in Redis
                    redisTaskRepository.updateResult(task.getTaskId(), executionResult, finalStatus);
                    logger.info("Task {} finished with status {} and result: {}", task.getTaskId(), finalStatus, executionResult);

                }
            } catch (JsonProcessingException e) {
                logger.error("Failed to deserialize task JSON", e);
            } catch (Exception e) {
                logger.error("Error while polling or processing tasks", e);
            }
        }
        logger.info("TaskProcessor stopped polling for agent: {}", AGENT_ID);
    }

    @PreDestroy
    public void destroy() {
        logger.info("Shutting down TaskProcessor...");
        running = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("TaskProcessor shut down.");
    }
}
