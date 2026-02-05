package io.github.tiamatxu.distributedtask.common.enums;

/**
 * 任务状态枚举
 * eg: PENDING, SCHEDULED, DISPATCHED, RUNNING, SUCCESS, FAILED, CANCELLED
 */
public enum TaskStatus {
    /**
     * 待调度
     */
    PENDING,
    /**
     * 已被调度器选中
     */
    SCHEDULED,
    /**
     * 已发送给 Worker Agent
     */
    DISPATCHED,
    /**
     * 正在 Worker Agent 上执行
     */
    RUNNING,
    /**
     * 执行成功
     */
    SUCCESS,
    /**
     * 执行失败
     */
    FAILED,
    /**
     * 任务被取消
     */
    CANCELLED
}