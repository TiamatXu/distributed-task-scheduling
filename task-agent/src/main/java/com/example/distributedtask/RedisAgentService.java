package com.example.distributedtask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisAgentService {

    private static final Logger log = LoggerFactory.getLogger(RedisAgentService.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisAgentService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        log.info("RedisAgentService initialized. Connected to Redis at {}:{}",
                 stringRedisTemplate.getConnectionFactory().getConnection().ping(),
                 stringRedisTemplate.getConnectionFactory().getConnection().getClientName());
    }

    public void setValue(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
        log.info("Set key: {}, value: {}", key, value);
    }

    public String getValue(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        log.info("Get key: {}, value: {}", key, value);
        return value;
    }
}
