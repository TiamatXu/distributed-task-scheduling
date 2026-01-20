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
        PENDING,      // 任务待调度
        SCHEDULED,    // 任务已被调度器选中
        DISPATCHED,   // 任务已发送给 Agent
        RUNNING,      // 任务正在 Agent 上执行
        SUCCESS,      // 任务执行成功
        FAILED,       // 任务执行失败
        CANCELLED     // 任务被取消
    }
}
