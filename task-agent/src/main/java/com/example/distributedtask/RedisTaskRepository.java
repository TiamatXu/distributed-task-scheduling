package com.example.distributedtask;

import com.example.distributedtask.task.Task;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class RedisTaskRepository {

    private static final Logger logger = LoggerFactory.getLogger(RedisTaskRepository.class);
    private static final String TASK_KEY_PREFIX = "task:";

    private final StringRedisTemplate stringRedisTemplate;
    private final HashOperations<String, String, String> hashOperations;
    private final ObjectMapper objectMapper;

    public RedisTaskRepository(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.hashOperations = stringRedisTemplate.opsForHash();
        this.objectMapper = objectMapper;
    }

    public void save(Task task) {
        try {
            String taskId = task.getTaskId();
            String taskHashKey = TASK_KEY_PREFIX + taskId;
            Map<String, String> taskMap = objectMapper.convertValue(task, new TypeReference<Map<String, String>>() {
            });
            hashOperations.putAll(taskHashKey, taskMap);
            logger.debug("Saved task {} to Redis Hash {}", taskId, taskHashKey);
        } catch (Exception e) {
            logger.error("Failed to save task {} to Redis", task.getTaskId(), e);
        }
    }

    public Task findById(String taskId) {
        String taskHashKey = TASK_KEY_PREFIX + taskId;
        Map<String, String> taskMap = hashOperations.entries(taskHashKey);
        if (taskMap.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.convertValue(taskMap, Task.class);
        } catch (Exception e) {
            logger.error("Failed to deserialize task {} from Redis", taskId, e);
            return null;
        }
    }

    public void updateStatus(String taskId, Task.TaskStatus status) {
        String taskHashKey = TASK_KEY_PREFIX + taskId;
        hashOperations.put(taskHashKey, "status", status.name());
        logger.debug("Updated status of task {} to {}", taskId, status);
    }

    public void updateResult(String taskId, String result, Task.TaskStatus status) {
        String taskHashKey = TASK_KEY_PREFIX + taskId;
        Map<String, String> updates = new HashMap<>();
        updates.put("result", result);
        updates.put("status", status.name());
        updates.put("finishTime", String.valueOf(System.currentTimeMillis()));
        hashOperations.putAll(taskHashKey, updates);
        logger.debug("Updated result and status of task {} to {} with result {}", taskId, status, result);
    }
}
