package com.example.distributedtask;

import com.example.distributedtask.task.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootApplication
public class TaskAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskAgentApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(RedisAgentService redisAgentService, ShellTaskExecutor shellTaskExecutor,
                                 StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper,
                                 RedisTaskRepository redisTaskRepository) {
        return args -> {
            System.out.println("--- Starting TaskAgentApplication Test ---");

            // 1. Create a sample Task
            Task sampleTask = new Task();
            sampleTask.setName("Test Shell Task");
            sampleTask.setType(Task.TaskType.SHELL);
            sampleTask.setPayload("echo Hello from produced task! && sleep 1 && echo Task completed.");
            sampleTask.setRetryCount(0); // No retries for this test

            System.out.println("Created sample task with ID: " + sampleTask.getTaskId());

            // 2. Serialize Task to JSON
            String taskJson = objectMapper.writeValueAsString(sampleTask);
            System.out.println("Serialized task JSON: " + taskJson);

            // 3. Push task to Redis queue (simulate external producer)
            String taskQueueKey = "agent:tasks:agent-001"; // Must match TaskProcessor's AGENT_ID
            stringRedisTemplate.opsForList().leftPush(taskQueueKey, taskJson);
            System.out.println("Pushed sample task to Redis list: " + taskQueueKey);

            System.out.println("\n--- Task Processor will pick up the task shortly ---");
            // Give some time for TaskProcessor to pick up and process the task
            Thread.sleep(5000); // Wait 5 seconds

            // 4. Retrieve the task's final state from Redis (simulate manager querying status)
            Task finalTaskState = redisTaskRepository.findById(sampleTask.getTaskId());
            if (finalTaskState != null) {
                System.out.println("\n--- Retrieved final task state from Redis ---");
                System.out.println("Task ID: " + finalTaskState.getTaskId());
                System.out.println("Task Status: " + finalTaskState.getStatus());
                System.out.println("Task Result: " + finalTaskState.getResult());
                System.out.println("Finish Time: " + (finalTaskState.getFinishTime() != null ? new java.util.Date(finalTaskState.getFinishTime()) : "N/A"));

                if (finalTaskState.getStatus() == Task.TaskStatus.SUCCESS && finalTaskState.getResult().contains("Task completed.")) {
                    System.out.println("Full MVP flow verified successfully!");
                } else {
                    System.err.println("MVP flow verification failed. Check task status/result.");
                }
            } else {
                System.err.println("Could not retrieve task {} from Redis after processing." + sampleTask.getTaskId());
            }

            System.out.println("\n--- TaskAgentApplication Test Finished ---");
        };
    }
}



