package io.github.tiamatxu.distributedtask.common.dto;

import io.github.tiamatxu.distributedtask.common.enums.TaskStatus;
import io.github.tiamatxu.distributedtask.common.enums.TaskType;
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
}
