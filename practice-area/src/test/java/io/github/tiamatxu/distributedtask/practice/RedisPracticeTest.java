package io.github.tiamatxu.distributedtask.practice;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisPracticeTest {

    @Container
    public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:6.2.6-alpine"))
            .withExposedPorts(6379);

    private static RedisPractice redisPractice;
    private static JedisPool jedisPool;

    @BeforeAll
    static void setUp() {
        String host = redis.getHost();
        int port = redis.getFirstMappedPort();
        jedisPool = new JedisPool(host, port);
        redisPractice = new RedisPractice(jedisPool);
    }

    @AfterAll
    static void tearDown() {
        jedisPool.close();
    }

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
    void testDeleteAndExists() {
        assertTrue(redisPractice.keyExists(testStringKey));
        Long deleteCount = redisPractice.deleteKeys(testStringKey, testHashKey);
        assertEquals(2, deleteCount);
        
        assertFalse(redisPractice.keyExists(testStringKey));
        assertNull(redisPractice.getString(testStringKey));
    }
}
