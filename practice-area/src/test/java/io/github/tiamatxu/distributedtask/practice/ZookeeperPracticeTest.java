package io.github.tiamatxu.distributedtask.practice;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZookeeperPracticeTest {

    private static ZookeeperPractice zookeeperPractice;
    private static CuratorFramework client;

    @BeforeAll
    static void setUp() throws IOException {
        // 从 application.properties 文件加载配置
        Properties props = new Properties();
        try (InputStream input = ZookeeperPracticeTest.class.getClassLoader().getResourceAsStream("application.properties")) {
            props.load(input);
        }

        String connectionString = props.getProperty("zookeeper.connectionString", "localhost:2181");
        
        System.out.println("Connecting to ZooKeeper at " + connectionString);

        client = CuratorFrameworkFactory.newClient(connectionString, new ExponentialBackoffRetry(1000, 3));
        client.start();
        zookeeperPractice = new ZookeeperPractice(client);
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }
    
    // --- 以下的测试方法与之前完全相同, 无需改动 ---

    private final String testPath = "/test-node-static";
    private final String testData = "hello, zookeeper";
    private final String ephemeralPath = "/ephemeral-node-static";

    @Test
    @Order(1)
    void testCreateAndGetNode() throws Exception {
        // 先清理, 避免重复运行导致节点已存在
        if (zookeeperPractice.nodeExists(testPath)) {
            zookeeperPractice.deleteNode(testPath);
        }
        
        zookeeperPractice.createNode(testPath, testData);
        assertTrue(zookeeperPractice.nodeExists(testPath));
        String retrievedData = zookeeperPractice.getNodeData(testPath);
        assertEquals(testData, retrievedData);
    }

    @Test
    @Order(2)
    void testUpdateNodeData() throws Exception {
        String updatedData = "updated data";
        zookeeperPractice.updateNodeData(testPath, updatedData);
        String retrievedData = zookeeperPractice.getNodeData(testPath);
        assertEquals(updatedData, retrievedData);
    }

    @Test
    @Order(3)
    void testCreateEphemeralNode() throws Exception {
        if (zookeeperPractice.nodeExists(ephemeralPath)) {
            zookeeperPractice.deleteNode(ephemeralPath);
        }
        zookeeperPractice.createEphemeralNode(ephemeralPath, "i am temporary");
        assertTrue(zookeeperPractice.nodeExists(ephemeralPath));
    }

    @Test
    @Order(4)
    void testGetChildren() throws Exception {
        zookeeperPractice.createNode(testPath + "/child1", "child1");
        zookeeperPractice.createNode(testPath + "/child2", "child2");
        List<String> children = zookeeperPractice.getChildren(testPath);
        assertEquals(2, children.size());
        assertTrue(children.contains("child1"));
        assertTrue(children.contains("child2"));
    }

    @Test
    @Order(5)
    void testDeleteNode() throws Exception {
        assertTrue(zookeeperPractice.nodeExists(testPath));
        zookeeperPractice.deleteNode(testPath);
        assertFalse(zookeeperPractice.nodeExists(testPath));
        
        // 临时节点在会话关闭后会自动删除, 这里无法直接测试, 仅验证删除持久节点
    }
}