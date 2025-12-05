package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a task to be executed by an agent team.
 * Team tasks are decomposed into subtasks and coordinated across multiple agents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamTask {

    /**
     * Unique identifier for this task.
     */
    private String id;

    /**
     * ID of the team executing this task.
     */
    private String teamId;

    /**
     * ID of the conversation this task belongs to (if any).
     */
    private String conversationId;

    /**
     * The input/prompt for the task.
     */
    private String input;

    /**
     * Additional context for the task.
     */
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    /**
     * User ID who submitted the task.
     */
    private String userId;

    /**
     * Current status of the task.
     */
    @Builder.Default
    private TeamTaskStatus status = TeamTaskStatus.PENDING;

    /**
     * Subtasks created from decomposition.
     */
    @Builder.Default
    private List<SubTask> subTasks = new ArrayList<>();

    /**
     * Final aggregated output.
     */
    private String finalOutput;

    /**
     * Structured final output.
     */
    private Map<String, Object> structuredOutput;

    /**
     * Overall confidence in the result (0.0 - 1.0).
     */
    private Double confidence;

    /**
     * Priority of this task.
     */
    @Builder.Default
    private int priority = 0;

    /**
     * Timeout for the task.
     */
    @Builder.Default
    private Duration timeout = Duration.ofMinutes(10);

    /**
     * Coordination pattern used.
     */
    private CoordinationConfig.CoordinationPattern coordinationPattern;

    /**
     * Current debate round (for debate teams).
     */
    private Integer currentDebateRound;

    /**
     * Current consensus level (for debate teams).
     */
    private Double consensusLevel;

    /**
     * PLATO plan ID (if governance is involved).
     */
    private String platoPlanId;

    /**
     * PLATO approval status.
     */
    private String platoApprovalStatus;

    /**
     * CAPSULE trace ID for replay.
     */
    private String capsuleTraceId;

    /**
     * Token budget for this task.
     */
    @Builder.Default
    private int tokenBudget = 50000;

    /**
     * Tokens used so far.
     */
    @Builder.Default
    private int tokensUsed = 0;

    /**
     * Error message (if failed).
     */
    private String errorMessage;

    /**
     * Warnings generated during execution.
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * When the task was created.
     */
    private Instant createdAt;

    /**
     * When the task started executing.
     */
    private Instant startedAt;

    /**
     * When the task completed.
     */
    private Instant completedAt;

    /**
     * When the task was last updated.
     */
    private Instant updatedAt;

    /**
     * Additional metadata.
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Namespace for multi-tenancy.
     */
    @Builder.Default
    private String namespace = "default";

    /**
     * Correlation ID for distributed tracing.
     */
    private String correlationId;

    /**
     * Check if the task is in a terminal state.
     */
    public boolean isTerminal() {
        return status == TeamTaskStatus.COMPLETED ||
               status == TeamTaskStatus.FAILED ||
               status == TeamTaskStatus.CANCELLED ||
               status == TeamTaskStatus.TIMEOUT ||
               status == TeamTaskStatus.REJECTED;
    }

    /**
     * Check if the task is currently executing.
     */
    public boolean isExecuting() {
        return status == TeamTaskStatus.EXECUTING ||
               status == TeamTaskStatus.DECOMPOSING ||
               status == TeamTaskStatus.DEBATING ||
               status == TeamTaskStatus.AGGREGATING;
    }

    /**
     * Check if the task has remaining token budget.
     */
    public boolean hasRemainingBudget() {
        return tokensUsed < tokenBudget;
    }

    /**
     * Get remaining token budget.
     */
    public int getRemainingBudget() {
        return Math.max(0, tokenBudget - tokensUsed);
    }

    /**
     * Add token usage.
     */
    public void addTokenUsage(int tokens) {
        this.tokensUsed += tokens;
    }

    /**
     * Get execution duration.
     */
    public Duration getDuration() {
        if (startedAt == null) {
            return Duration.ZERO;
        }
        Instant end = completedAt != null ? completedAt : Instant.now();
        return Duration.between(startedAt, end);
    }

    /**
     * Get number of completed subtasks.
     */
    public long getCompletedSubTaskCount() {
        if (subTasks == null) {
            return 0;
        }
        return subTasks.stream()
                .filter(st -> st.getStatus() == SubTaskStatus.COMPLETED)
                .count();
    }

    /**
     * Get number of pending subtasks.
     */
    public long getPendingSubTaskCount() {
        if (subTasks == null) {
            return 0;
        }
        return subTasks.stream()
                .filter(st -> !st.isTerminal())
                .count();
    }

    /**
     * Get subtask progress (0.0 - 1.0).
     */
    public double getProgress() {
        if (subTasks == null || subTasks.isEmpty()) {
            return 0.0;
        }
        return (double) getCompletedSubTaskCount() / subTasks.size();
    }

    /**
     * Add a subtask.
     */
    public void addSubTask(SubTask subTask) {
        if (subTasks == null) {
            subTasks = new ArrayList<>();
        }
        subTask.setParentTaskId(this.id);
        subTask.setSequenceNumber(subTasks.size());
        subTasks.add(subTask);
        this.updatedAt = Instant.now();
    }

    /**
     * Find a subtask by ID.
     */
    public SubTask findSubTask(String subTaskId) {
        if (subTasks == null) {
            return null;
        }
        return subTasks.stream()
                .filter(st -> st.getId().equals(subTaskId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get next executable subtasks (dependencies satisfied).
     */
    public List<SubTask> getExecutableSubTasks() {
        if (subTasks == null) {
            return List.of();
        }
        return subTasks.stream()
                .filter(st -> st.getStatus() == SubTaskStatus.PENDING || st.getStatus() == SubTaskStatus.ASSIGNED)
                .filter(st -> st.dependenciesSatisfied(subTasks))
                .toList();
    }

    /**
     * Add a warning.
     */
    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }

    // Status transition methods

    /**
     * Mark task as decomposing.
     */
    public void markDecomposing() {
        this.status = TeamTaskStatus.DECOMPOSING;
        this.startedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Mark task as awaiting approval.
     */
    public void markAwaitingApproval(String platoPlanId) {
        this.status = TeamTaskStatus.AWAITING_APPROVAL;
        this.platoPlanId = platoPlanId;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark task as approved.
     */
    public void markApproved() {
        this.status = TeamTaskStatus.APPROVED;
        this.platoApprovalStatus = "APPROVED";
        this.updatedAt = Instant.now();
    }

    /**
     * Mark task as rejected.
     */
    public void markRejected(String reason) {
        this.status = TeamTaskStatus.REJECTED;
        this.platoApprovalStatus = "REJECTED";
        this.errorMessage = reason;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Mark task as executing.
     */
    public void markExecuting() {
        this.status = TeamTaskStatus.EXECUTING;
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Mark task as debating.
     */
    public void markDebating(int round) {
        this.status = TeamTaskStatus.DEBATING;
        this.currentDebateRound = round;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark task as aggregating.
     */
    public void markAggregating() {
        this.status = TeamTaskStatus.AGGREGATING;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark task as completed.
     */
    public void markCompleted(String output, Double confidence) {
        this.status = TeamTaskStatus.COMPLETED;
        this.finalOutput = output;
        this.confidence = confidence;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Mark task as failed.
     */
    public void markFailed(String errorMessage) {
        this.status = TeamTaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Mark task as timed out.
     */
    public void markTimeout() {
        this.status = TeamTaskStatus.TIMEOUT;
        this.errorMessage = "Task execution timed out";
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Mark task as cancelled.
     */
    public void markCancelled() {
        this.status = TeamTaskStatus.CANCELLED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Create a new team task.
     */
    public static TeamTask create(String teamId, String input, String userId) {
        return TeamTask.builder()
                .id(UUID.randomUUID().toString())
                .teamId(teamId)
                .input(input)
                .userId(userId)
                .status(TeamTaskStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .correlationId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Create a new team task with context.
     */
    public static TeamTask create(String teamId, String input, String userId, Map<String, Object> context) {
        TeamTask task = create(teamId, input, userId);
        task.setContext(context != null ? context : new HashMap<>());
        return task;
    }
}
