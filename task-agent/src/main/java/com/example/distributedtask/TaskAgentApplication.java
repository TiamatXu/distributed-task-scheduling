package com.example.distributedtask;

import com.example.distributedtask.task.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootApplication
public class TaskAgentApplication {

    private static final Logger logger = LoggerFactory.getLogger(TaskAgentApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(TaskAgentApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(RedisAgentService redisAgentService, ShellTaskExecutor shellTaskExecutor,
                                 StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper,
                                 RedisTaskRepository redisTaskRepository) {
        return args -> {
            logger.info("--- Starting TaskAgentApplication Test ---");

            // 1. Create a sample Task
            Task sampleTask = new Task();
            sampleTask.setName("Test Shell Task");
            sampleTask.setType(Task.TaskType.SHELL);
            sampleTask.setPayload("echo Hello from produced task! && sleep 1 && echo Task completed.");
            sampleTask.setRetryCount(0); // No retries for this test

            logger.info("Created sample task with ID: {}", sampleTask.getTaskId());

            // 2. Serialize Task to JSON
            String taskJson = objectMapper.writeValueAsString(sampleTask);
            logger.info("Serialized task JSON: {}", taskJson);

            // 3. Push task to Redis queue (simulate external producer)
            String taskQueueKey = "agent:tasks:agent-001"; // Must match TaskProcessor's AGENT_ID
            stringRedisTemplate.opsForList().leftPush(taskQueueKey, taskJson);
            logger.info("Pushed sample task to Redis list: {}", taskQueueKey);

            logger.info("\n--- Task Processor will pick up the task shortly ---");
            // Give some time for TaskProcessor to pick up and process the task
            Thread.sleep(5000); // Wait 5 seconds

            // 4. Retrieve the task's final state from Redis (simulate manager querying status)
            Task finalTaskState = redisTaskRepository.findById(sampleTask.getTaskId());
            if (finalTaskState != null) {
                logger.info("\n--- Retrieved final task state from Redis ---");
                logger.info("Task ID: {}", finalTaskState.getTaskId());
                logger.info("Task Status: {}", finalTaskState.getStatus());
                logger.info("Task Result: {}", finalTaskState.getResult());
                logger.info("Finish Time: {}", finalTaskState.getFinishTime() != null ? new java.util.Date(finalTaskState.getFinishTime()) : "N/A");

                if (finalTaskState.getStatus() == Task.TaskStatus.SUCCESS && finalTaskState.getResult().contains("Taskcompleted.")) {
                    logger.info("Full MVP flow verified successfully!");
                } else {
                    logger.error("MVP flow verification failed. Check task status/result.");
                }
            } else {
                logger.error("Could not retrieve task {} from Redis after processing.", sampleTask.getTaskId());
            }

            logger.info("\n--- TaskAgentApplication Test Finished ---");
        };
    }
}



