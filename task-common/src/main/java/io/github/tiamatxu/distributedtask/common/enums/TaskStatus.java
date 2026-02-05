package io.github.tiamatxu.distributedtask.common.enums;

public enum TaskStatus {
        PENDING,      // 待调度
        SCHEDULED,    // 已被调度器选中
        DISPATCHED,   // 已发送给 Agent
        RUNNING,      // 正在 Agent 上执行
        SUCCESS,      // 执行成功
        FAILED,       // 执行失败
        CANCELLED     // 被取消
    }