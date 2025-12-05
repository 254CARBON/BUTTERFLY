package com.z254.butterfly.cortex.domain.model;

/**
 * Status of a subtask within a team task.
 */
public enum SubTaskStatus {

    /**
     * Subtask is pending assignment or execution.
     */
    PENDING,

    /**
     * Subtask has been assigned to an agent.
     */
    ASSIGNED,

    /**
     * Subtask is waiting for dependencies to complete.
     */
    BLOCKED,

    /**
     * Subtask is currently being executed.
     */
    EXECUTING,

    /**
     * Subtask is being reviewed by a critic agent.
     */
    REVIEWING,

    /**
     * Subtask needs revision based on feedback.
     */
    NEEDS_REVISION,

    /**
     * Subtask completed successfully.
     */
    COMPLETED,

    /**
     * Subtask failed.
     */
    FAILED,

    /**
     * Subtask was skipped (e.g., not needed).
     */
    SKIPPED,

    /**
     * Subtask was cancelled.
     */
    CANCELLED
}
