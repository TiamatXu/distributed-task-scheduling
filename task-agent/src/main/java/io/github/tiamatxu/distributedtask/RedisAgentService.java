package io.github.tiamatxu.distributedtask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisAgentService {

    private static final Logger logger = LoggerFactory.getLogger(RedisAgentService.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisAgentService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        assert stringRedisTemplate.getConnectionFactory() != null;
        stringRedisTemplate.getConnectionFactory().getConnection().setClientName("RedisAgentServiceClient".getBytes());
        logger.info("RedisAgentService initialized. Connected to Redis at {}:{}",
                stringRedisTemplate.getConnectionFactory().getConnection().ping(),
                stringRedisTemplate.getConnectionFactory().getConnection().getClientName());
    }

    public void setValue(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
        logger.info("Set key: {}, value: {}", key, value);
    }

    public String getValue(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        logger.info("Get key: {}, value: {}", key, value);
        return value;
    }
}
