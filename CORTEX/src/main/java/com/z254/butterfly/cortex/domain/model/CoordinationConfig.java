package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 * Configuration for multi-agent coordination behavior.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoordinationConfig {

    /**
     * Coordination pattern to use.
     */
    @Builder.Default
    private CoordinationPattern pattern = CoordinationPattern.HIERARCHICAL;

    /**
     * Maximum number of subtasks to create from a single task.
     */
    @Builder.Default
    private int maxSubTasks = 10;

    /**
     * Maximum depth of task decomposition.
     */
    @Builder.Default
    private int maxDecompositionDepth = 3;

    /**
     * Maximum number of debate rounds.
     */
    @Builder.Default
    private int maxDebateRounds = 5;

    /**
     * Consensus threshold for debate (0.0 - 1.0).
     * The minimum agreement level required to reach consensus.
     */
    @Builder.Default
    private double consensusThreshold = 0.7;

    /**
     * Timeout for the entire team task.
     */
    @Builder.Default
    private Duration taskTimeout = Duration.ofMinutes(10);

    /**
     * Timeout for individual subtasks.
     */
    @Builder.Default
    private Duration subTaskTimeout = Duration.ofMinutes(3);

    /**
     * Maximum concurrent subtasks.
     */
    @Builder.Default
    private int maxConcurrentSubTasks = 5;

    /**
     * Whether to allow task delegation between agents.
     */
    @Builder.Default
    private boolean allowDelegation = true;

    /**
     * Whether to enable critic review of outputs.
     */
    @Builder.Default
    private boolean enableCriticReview = true;

    /**
     * Minimum confidence for accepting a subtask result.
     */
    @Builder.Default
    private double minResultConfidence = 0.6;

    /**
     * Maximum retries for failed subtasks.
     */
    @Builder.Default
    private int maxRetries = 2;

    /**
     * Whether to require governance approval for the plan.
     */
    @Builder.Default
    private boolean requireGovernanceApproval = false;

    /**
     * Whether to persist the team conversation to CAPSULE.
     */
    @Builder.Default
    private boolean persistToCapsule = true;

    /**
     * Token budget for the entire team task.
     */
    @Builder.Default
    private int tokenBudget = 50000;

    /**
     * Token budget per agent per task.
     */
    @Builder.Default
    private int tokenBudgetPerAgent = 10000;

    /**
     * Whether to enable parallel execution where possible.
     */
    @Builder.Default
    private boolean enableParallelExecution = true;

    /**
     * Aggregation strategy for combining results.
     */
    @Builder.Default
    private AggregationStrategy aggregationStrategy = AggregationStrategy.WEIGHTED_CONFIDENCE;

    /**
     * Fallback behavior when coordination fails.
     */
    @Builder.Default
    private FallbackBehavior fallbackBehavior = FallbackBehavior.SINGLE_AGENT;

    /**
     * Maximum messages in a team conversation.
     */
    @Builder.Default
    private int maxMessages = 100;

    /**
     * Whether to deduplicate similar messages.
     */
    @Builder.Default
    private boolean deduplicateMessages = true;

    /**
     * Coordination patterns available.
     */
    public enum CoordinationPattern {
        /**
         * Orchestrator decomposes tasks and delegates to specialists.
         */
        HIERARCHICAL,

        /**
         * Agents argue different positions and reach consensus.
         */
        DEBATE,

        /**
         * Sequential refinement through a chain of agents.
         */
        CHAIN,

        /**
         * Independent execution with result aggregation.
         */
        PARALLEL,

        /**
         * Agents self-organize around the task.
         */
        SWARM
    }

    /**
     * Strategies for aggregating results from multiple agents.
     */
    public enum AggregationStrategy {
        /**
         * Weight results by confidence scores.
         */
        WEIGHTED_CONFIDENCE,

        /**
         * Use majority voting.
         */
        MAJORITY_VOTE,

        /**
         * Take the result with highest confidence.
         */
        HIGHEST_CONFIDENCE,

        /**
         * Synthesize all results into a unified output.
         */
        SYNTHESIS,

        /**
         * Chain results sequentially.
         */
        SEQUENTIAL,

        /**
         * Use the orchestrator's judgment.
         */
        ORCHESTRATOR_DECISION
    }

    /**
     * Fallback behaviors when coordination fails.
     */
    public enum FallbackBehavior {
        /**
         * Fall back to single-agent execution.
         */
        SINGLE_AGENT,

        /**
         * Retry with different agents.
         */
        RETRY_DIFFERENT_AGENTS,

        /**
         * Return partial results.
         */
        PARTIAL_RESULTS,

        /**
         * Fail the entire task.
         */
        FAIL
    }

    /**
     * Default configuration for hierarchical teams.
     */
    public static CoordinationConfig hierarchical() {
        return CoordinationConfig.builder()
                .pattern(CoordinationPattern.HIERARCHICAL)
                .enableCriticReview(true)
                .enableParallelExecution(true)
                .aggregationStrategy(AggregationStrategy.ORCHESTRATOR_DECISION)
                .build();
    }

    /**
     * Default configuration for debate teams.
     */
    public static CoordinationConfig debate() {
        return CoordinationConfig.builder()
                .pattern(CoordinationPattern.DEBATE)
                .maxDebateRounds(5)
                .consensusThreshold(0.7)
                .enableCriticReview(false) // Agents critique each other
                .aggregationStrategy(AggregationStrategy.MAJORITY_VOTE)
                .build();
    }

    /**
     * Default configuration for chain teams.
     */
    public static CoordinationConfig chain() {
        return CoordinationConfig.builder()
                .pattern(CoordinationPattern.CHAIN)
                .enableParallelExecution(false)
                .aggregationStrategy(AggregationStrategy.SEQUENTIAL)
                .build();
    }

    /**
     * Default configuration for parallel teams.
     */
    public static CoordinationConfig parallel() {
        return CoordinationConfig.builder()
                .pattern(CoordinationPattern.PARALLEL)
                .enableParallelExecution(true)
                .enableCriticReview(false)
                .aggregationStrategy(AggregationStrategy.SYNTHESIS)
                .build();
    }
}
