package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Represents a task to be executed by an agent.
 * A task is a single execution request with input, context, and tracking information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTask {

    /**
     * Unique identifier for this task.
     */
    private String id;

    /**
     * ID of the agent executing this task.
     */
    private String agentId;

    /**
     * ID of the conversation this task belongs to (if any).
     */
    private String conversationId;

    /**
     * User input/query for this task.
     */
    private String input;

    /**
     * Correlation ID for distributed tracing.
     */
    private String correlationId;

    /**
     * Idempotency key for deduplication.
     */
    private String idempotencyKey;

    /**
     * User ID who initiated this task.
     */
    private String userId;

    /**
     * Namespace context for this task.
     */
    private String namespace;

    /**
     * Additional context for the task.
     */
    private Map<String, Object> context;

    /**
     * Current status of the task.
     */
    private TaskStatus status;

    /**
     * Priority of this task (higher = more urgent).
     */
    private int priority;

    /**
     * Maximum duration allowed for this task.
     */
    private Duration timeout;

    /**
     * PLATO plan ID if this task is part of a governance plan.
     */
    private String platoPlanId;

    /**
     * PLATO step ID within the plan.
     */
    private String platoStepId;

    /**
     * RIM node ID this task relates to.
     */
    private String rimNodeId;

    /**
     * When the task was created.
     */
    private Instant createdAt;

    /**
     * When the task was started.
     */
    private Instant startedAt;

    /**
     * When the task was completed.
     */
    private Instant completedAt;

    /**
     * Service that requested this task.
     */
    private String requesterService;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    /**
     * Task status enumeration.
     */
    public enum TaskStatus {
        PENDING,      // Task is pending, waiting to start
        QUEUED,       // Task is queued for execution
        RUNNING,      // Task is currently executing
        WAITING_APPROVAL, // Task is waiting for governance approval
        APPROVED,     // Task has been approved
        COMPLETED,    // Task completed successfully
        FAILED,       // Task failed
        TIMEOUT,      // Task timed out
        CANCELLED,    // Task was cancelled
        REJECTED      // Task was rejected by governance
    }

    /**
     * Check if this task is in a terminal state.
     */
    public boolean isTerminal() {
        return status == TaskStatus.COMPLETED ||
               status == TaskStatus.FAILED ||
               status == TaskStatus.TIMEOUT ||
               status == TaskStatus.CANCELLED ||
               status == TaskStatus.REJECTED;
    }

    /**
     * Check if this task is currently running.
     */
    public boolean isRunning() {
        return status == TaskStatus.RUNNING;
    }

    /**
     * Check if this task can be cancelled.
     */
    public boolean canCancel() {
        return status == TaskStatus.PENDING ||
               status == TaskStatus.QUEUED ||
               status == TaskStatus.RUNNING ||
               status == TaskStatus.WAITING_APPROVAL;
    }

    /**
     * Get the duration of this task (if completed).
     */
    public Duration getDuration() {
        if (startedAt == null) {
            return Duration.ZERO;
        }
        Instant end = completedAt != null ? completedAt : Instant.now();
        return Duration.between(startedAt, end);
    }
}
