package com.z254.butterfly.cortex.domain.model;

/**
 * Status of a team task execution.
 */
public enum TeamTaskStatus {

    /**
     * Task has been created but not yet started.
     */
    PENDING,

    /**
     * Task is being decomposed into subtasks.
     */
    DECOMPOSING,

    /**
     * Task is waiting for governance approval.
     */
    AWAITING_APPROVAL,

    /**
     * Task has been approved and is ready to execute.
     */
    APPROVED,

    /**
     * Task is currently being executed by the team.
     */
    EXECUTING,

    /**
     * Agents are debating to reach consensus.
     */
    DEBATING,

    /**
     * Results are being aggregated from multiple agents.
     */
    AGGREGATING,

    /**
     * Task completed successfully.
     */
    COMPLETED,

    /**
     * Task failed during execution.
     */
    FAILED,

    /**
     * Task was cancelled.
     */
    CANCELLED,

    /**
     * Task timed out.
     */
    TIMEOUT,

    /**
     * Task was rejected by governance.
     */
    REJECTED
}
