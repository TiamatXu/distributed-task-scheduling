package com.example.distributedtask.task;

import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

@Data
public class Task implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;
    private String name;
    private TaskType type;
    private String payload; // e.g., shell command, Python script content
    private TaskStatus status;
    private String agentId; // Which agent is executing the task
    private String result; // Execution result
    private Long creationTime;
    private Long startTime;
    private Long finishTime;
    private int retryCount;

    public Task() {
        this.taskId = UUID.randomUUID().toString();
        this.creationTime = System.currentTimeMillis();
        this.status = TaskStatus.PENDING;
    }

    public enum TaskType {
        SHELL,
        PYTHON_SCRIPT
    }

    public enum TaskStatus {
        PENDING,      // 待调度
        SCHEDULED,    // 已被调度器选中
        DISPATCHED,   // 已发送给 Agent
        RUNNING,      // 正在 Agent 上执行
        SUCCESS,      // 执行成功
        FAILED,       // 执行失败
        CANCELLED     // 被取消
    }
}
