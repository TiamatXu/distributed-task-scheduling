package io.github.tiamatxu.distributedtask.practice;

import org.junit.jupiter.api.*;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisPracticeTest {

    private static RedisPractice redisPractice;
    private static JedisPool jedisPool;

    @BeforeAll
    static void setUp() throws IOException {
        // 从 application.properties 文件加载配置
        Properties props = new Properties();
        try (InputStream input = RedisPracticeTest.class.getClassLoader().getResourceAsStream("application.properties")) {
            props.load(input);
        }

        String host = props.getProperty("redis.host", "localhost");
        String user = props.getProperty("redis.user", "");
        String password = props.getProperty("redis.password", "");
        int port = Integer.parseInt(props.getProperty("redis.port", "6379"));
        
        System.out.println("Connecting to Redis at " + host + ":" + port);

        jedisPool = new JedisPool(host, port);
        redisPractice = new RedisPractice(jedisPool);
    }

    @AfterAll
    static void tearDown() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    // --- 以下的测试方法与之前完全相同, 无需改动 ---

    private final String testStringKey = "test:string";
    private final String testStringValue = "hello, redis";
    private final String testHashKey = "test:hash";
    private final String testListKey = "test:list";
    private final String testSetKey = "test:set";

    @Test
    @Order(1)
    void testSetAndGetString() {
        String result = redisPractice.setString(testStringKey, testStringValue);
        assertEquals("OK", result);

        String retrievedValue = redisPractice.getString(testStringKey);
        assertEquals(testStringValue, retrievedValue);
    }
    
    @Test
    @Order(6) // 将删除操作放到最后, 以免影响其他测试
    void testCleanup() {
        // 清理本次测试创建的所有 key
        redisPractice.deleteKeys(testStringKey, testHashKey, testListKey, testSetKey);
        assertFalse(redisPractice.keyExists(testStringKey));
        assertFalse(redisPractice.keyExists(testHashKey));
        assertFalse(redisPractice.keyExists(testListKey));
        assertFalse(redisPractice.keyExists(testSetKey));
    }

    @Test
    @Order(2)
    void testSetAndGetHash() {
        Long setResult = redisPractice.setHashField(testHashKey, "name", "redis-hash");
        assertEquals(1, setResult);
        setResult = redisPractice.setHashField(testHashKey, "version", "6.2");
        assertEquals(1, setResult);

        String name = redisPractice.getHashField(testHashKey, "name");
        assertEquals("redis-hash", name);

        Map<String, String> allFields = redisPractice.getHashAll(testHashKey);
        assertEquals(2, allFields.size());
        assertEquals("6.2", allFields.get("version"));
    }

    @Test
    @Order(3)
    void testListOperations() {
        // 先清理, 避免重复运行测试导致 key 已存在
        redisPractice.deleteKeys(testListKey);
        
        Long pushResult = redisPractice.pushToListLeft(testListKey, "c", "b", "a");
        assertEquals(3, pushResult);

        List<String> allItems = redisPractice.getListAll(testListKey);
        assertArrayEquals(new String[]{"a", "b", "c"}, allItems.toArray());

        String poppedItem = redisPractice.popFromListRight(testListKey);
        assertEquals("c", poppedItem);
    }

    @Test
    @Order(4)
    void testSetOperations() {
        // 先清理
        redisPractice.deleteKeys(testSetKey);

        Long addResult = redisPractice.addMembersToSet(testSetKey, "apple", "banana", "orange");
        assertEquals(3, addResult);
        // 重复添加, 返回0
        addResult = redisPractice.addMembersToSet(testSetKey, "apple");
        assertEquals(0, addResult);

        assertTrue(redisPractice.isMemberOfSet(testSetKey, "banana"));
        assertFalse(redisPractice.isMemberOfSet(testSetKey, "grape"));

        assertEquals(3, redisPractice.getSetAll(testSetKey).size());
    }

    @Test
    @Order(5)
    void testKeyExists() {
        assertTrue(redisPractice.keyExists(testStringKey));
        assertFalse(redisPractice.keyExists("a-non-existent-key"));
    }
}