package io.github.tiamatxu.distributedtask.practice;

import lombok.AllArgsConstructor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 使用 Jedis 练习 Redis 基础命令
 * 这个类不是线程安全的, 仅用于练习目的。
 */
@AllArgsConstructor
public class RedisPractice {

    private JedisPool jedisPool;

    /**
     * 设置 String
     * 命令: SET key value
     */
    public String setString(String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.set(key, value);
        }
    }

    /**
     * 获取 String
     * 命令: GET key
     */
    public String getString(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        }
    }

    /**
     * 设置 Hash 中的一个字段
     * 命令: HSET key field value
     */
    public Long setHashField(String key, String field, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hset(key, field, value);
        }
    }

    /**
     * 获取 Hash 中的一个字段
     * 命令: HGET key field
     */
    public String getHashField(String key, String field) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hget(key, field);
        }
    }

    /**
     * 获取 Hash 中的所有字段和值
     * 命令: HGETALL key
     */
    public Map<String, String> getHashAll(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hgetAll(key);
        }
    }

    /**
     * 向 List 左侧推入一个或多个值
     * 命令: LPUSH key value [value ...]
     */
    public Long pushToListLeft(String key, String... values) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.lpush(key, values);
        }
    }

    /**
     * 从 List 右侧弹出一个值
     * 命令: RPOP key
     */
    public String popFromListRight(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.rpop(key);
        }
    }

    /**
     * 获取 List 的所有元素
     * 命令: LRANGE key 0 -1
     */
    public List<String> getListAll(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.lrange(key, 0, -1);
        }
    }

    /**
     * 向 Set 添加一个或多个成员
     * 命令: SADD key member [member ...]
     */
    public Long addMembersToSet(String key, String... members) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.sadd(key, members);
        }
    }

    /**
     * 判断一个成员是否存在于 Set 中
     * 命令: SISMEMBER key member
     */
    public Boolean isMemberOfSet(String key, String member) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.sismember(key, member);
        }
    }
    
    /**
     * 获取 Set 的所有成员
     * 命令: SMEMBERS key
     */
    public Set<String> getSetAll(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.smembers(key);
        }
    }

    /**
     * 删除一个或多个 key
     * 命令: DEL key [key ...]
     */
    public Long deleteKeys(String... keys) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.del(keys);
        }
    }

    /**
     * 检查 key 是否存在
     * 命令: EXISTS key
     */
    public Boolean keyExists(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(key);
        }
    }
}
