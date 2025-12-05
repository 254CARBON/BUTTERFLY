package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a subtask within a team task.
 * Subtasks are created by task decomposition and assigned to individual agents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubTask {

    /**
     * Unique identifier for this subtask.
     */
    private String id;

    /**
     * ID of the parent team task.
     */
    private String parentTaskId;

    /**
     * Sequence number within the parent task.
     */
    private int sequenceNumber;

    /**
     * Description of what this subtask should accomplish.
     */
    private String description;

    /**
     * Detailed input/instructions for the subtask.
     */
    private String input;

    /**
     * Expected output type or format.
     */
    private String expectedOutput;

    /**
     * ID of the agent assigned to this subtask.
     */
    private String assignedAgentId;

    /**
     * Required capabilities to execute this subtask.
     */
    @Builder.Default
    private Set<String> requiredCapabilities = new HashSet<>();

    /**
     * Required domain expertise.
     */
    @Builder.Default
    private Set<String> requiredDomains = new HashSet<>();

    /**
     * IDs of subtasks that must complete before this one can start.
     */
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();

    /**
     * Current status of the subtask.
     */
    @Builder.Default
    private SubTaskStatus status = SubTaskStatus.PENDING;

    /**
     * Result/output of the subtask.
     */
    private String result;

    /**
     * Confidence score of the result (0.0 - 1.0).
     */
    private Double resultConfidence;

    /**
     * Error message if the subtask failed.
     */
    private String errorMessage;

    /**
     * Feedback from critic agent (if reviewed).
     */
    private String feedback;

    /**
     * Number of times this subtask has been retried.
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * Maximum retries allowed.
     */
    @Builder.Default
    private int maxRetries = 2;

    /**
     * Priority (higher = more urgent).
     */
    @Builder.Default
    private int priority = 0;

    /**
     * Estimated complexity (0.0 - 1.0).
     */
    @Builder.Default
    private double complexity = 0.5;

    /**
     * When the subtask was created.
     */
    private Instant createdAt;

    /**
     * When the subtask was assigned.
     */
    private Instant assignedAt;

    /**
     * When the subtask started executing.
     */
    private Instant startedAt;

    /**
     * When the subtask completed.
     */
    private Instant completedAt;

    /**
     * Timeout for this subtask.
     */
    @Builder.Default
    private Duration timeout = Duration.ofMinutes(5);

    /**
     * Token usage for this subtask.
     */
    private Integer tokenUsage;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    /**
     * Check if this subtask is in a terminal state.
     */
    public boolean isTerminal() {
        return status == SubTaskStatus.COMPLETED ||
               status == SubTaskStatus.FAILED ||
               status == SubTaskStatus.SKIPPED ||
               status == SubTaskStatus.CANCELLED;
    }

    /**
     * Check if this subtask is blocked by dependencies.
     */
    public boolean isBlocked() {
        return status == SubTaskStatus.BLOCKED;
    }

    /**
     * Check if this subtask can be retried.
     */
    public boolean canRetry() {
        return status == SubTaskStatus.FAILED && retryCount < maxRetries;
    }

    /**
     * Check if dependencies are satisfied.
     */
    public boolean dependenciesSatisfied(List<SubTask> allSubTasks) {
        if (dependencies == null || dependencies.isEmpty()) {
            return true;
        }
        for (String depId : dependencies) {
            boolean found = false;
            for (SubTask other : allSubTasks) {
                if (other.getId().equals(depId)) {
                    if (other.getStatus() != SubTaskStatus.COMPLETED) {
                        return false;
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false; // Dependency not found
            }
        }
        return true;
    }

    /**
     * Get the execution duration.
     */
    public Duration getDuration() {
        if (startedAt == null) {
            return Duration.ZERO;
        }
        Instant end = completedAt != null ? completedAt : Instant.now();
        return Duration.between(startedAt, end);
    }

    /**
     * Mark as assigned.
     */
    public void markAssigned(String agentId) {
        this.assignedAgentId = agentId;
        this.status = SubTaskStatus.ASSIGNED;
        this.assignedAt = Instant.now();
    }

    /**
     * Mark as executing.
     */
    public void markExecuting() {
        this.status = SubTaskStatus.EXECUTING;
        this.startedAt = Instant.now();
    }

    /**
     * Mark as completed.
     */
    public void markCompleted(String result, Double confidence) {
        this.status = SubTaskStatus.COMPLETED;
        this.result = result;
        this.resultConfidence = confidence;
        this.completedAt = Instant.now();
    }

    /**
     * Mark as failed.
     */
    public void markFailed(String errorMessage) {
        this.status = SubTaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    /**
     * Mark for retry.
     */
    public void markForRetry() {
        this.status = SubTaskStatus.PENDING;
        this.retryCount++;
        this.assignedAgentId = null;
        this.assignedAt = null;
        this.startedAt = null;
        this.result = null;
        this.errorMessage = null;
    }
}
