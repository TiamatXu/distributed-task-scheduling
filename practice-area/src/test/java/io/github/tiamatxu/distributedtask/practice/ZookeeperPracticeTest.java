package io.github.tiamatxu.distributedtask.practice;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZookeeperPracticeTest {

    // ZooKeeper 镜像没有默认的 health check, 我们需要自己指定等待策略
    @Container
    public static GenericContainer<?> zookeeper = new GenericContainer<>(DockerImageName.parse("zookeeper:3.8.0"))
            .withExposedPorts(2181)
            .waitingFor(Wait.forCommand("echo", "ruok").withStartupTimeout(java.time.Duration.ofSeconds(30)).until(o -> o.contains("imok")));


    private static ZookeeperPractice zookeeperPractice;
    private static CuratorFramework client;

    @BeforeAll
    static void setUp() {
        String connectionString = zookeeper.getHost() + ":" + zookeeper.getFirstMappedPort();
        client = CuratorFrameworkFactory.newClient(connectionString, new ExponentialBackoffRetry(1000, 3));
        client.start();
        zookeeperPractice = new ZookeeperPractice(client);
    }

    @AfterAll
    static void tearDown() {
        client.close();
    }

    private final String testPath = "/test-node";
    private final String testData = "hello, zookeeper";
    private final String ephemeralPath = "/ephemeral-node";

    @Test
    @Order(1)
    void testCreateAndGetNode() throws Exception {
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
    }
}
