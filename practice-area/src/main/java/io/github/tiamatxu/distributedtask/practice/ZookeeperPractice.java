package io.github.tiamatxu.distributedtask.practice;

import lombok.AllArgsConstructor;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 使用 Apache Curator 练习 ZooKeeper 基础命令
 * 这个类是线程安全的, 因为 CuratorFramework 实例是线程安全的。
 */
@AllArgsConstructor
public class ZookeeperPractice {

    private CuratorFramework client;

    /**
     * 创建持久节点
     * 如果父节点不存在, 会自动创建
     * 命令: create /path data
     */
    public String createNode(String path, String data) throws Exception {
        return client.create()
                .creatingParentsIfNeeded() // 如果父节点不存在, 则自动创建
                .forPath(path, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 创建临时节点
     * 客户端会话结束后, 节点会自动删除
     * 命令: create -e /path data
     */
    public String createEphemeralNode(String path, String data) throws Exception {
        return client.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL)
                .forPath(path, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取节点数据
     * 命令: get /path
     */
    public String getNodeData(String path) throws Exception {
        byte[] bytes = client.getData().forPath(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 更新节点数据
     * 命令: set /path data
     */
    public Stat updateNodeData(String path, String data) throws Exception {
        return client.setData().forPath(path, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 检查节点是否存在
     * 命令: stat /path
     */
    public boolean nodeExists(String path) throws Exception {
        Stat stat = client.checkExists().forPath(path);
        return stat != null;
    }

    /**
     * 获取子节点列表
     * 命令: ls /path
     */
    public List<String> getChildren(String path) throws Exception {
        return client.getChildren().forPath(path);
    }

    /**
     * 删除节点
     * 命令: delete /path
     */
    public void deleteNode(String path) throws Exception {
        // 默认情况下, 只能删除叶子节点。
        // guaranteed() 会在后台持续尝试, 直到删除成功
        client.delete()
              .guaranteed()
              .deletingChildrenIfNeeded() // 递归删除所有子节点
              .forPath(path);
    }
}
