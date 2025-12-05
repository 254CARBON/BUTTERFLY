package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a thought/reasoning step from multi-agent team execution.
 * Used for streaming progress and visibility into team coordination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamThought {

    /**
     * Unique identifier for this thought.
     */
    private String id;

    /**
     * ID of the team task this thought belongs to.
     */
    private String teamTaskId;

    /**
     * ID of the subtask (if applicable).
     */
    private String subTaskId;

    /**
     * ID of the agent generating this thought.
     */
    private String agentId;

    /**
     * Name of the agent (for display).
     */
    private String agentName;

    /**
     * Role of the agent in the team.
     */
    private TeamRole agentRole;

    /**
     * Type of thought.
     */
    private TeamThoughtType type;

    /**
     * Content of the thought.
     */
    private String content;

    /**
     * Iteration/step number in the execution.
     */
    private int iteration;

    /**
     * Round number (for debate scenarios).
     */
    private int round;

    /**
     * Phase of team execution.
     */
    private String phase;

    /**
     * ID of related agent message (if this thought is about a message).
     */
    private String messageId;

    /**
     * Related tool ID (if this thought involves tool usage).
     */
    private String toolId;

    /**
     * Tool parameters (if applicable).
     */
    private Map<String, Object> toolParameters;

    /**
     * Tool result (if applicable).
     */
    private String toolResult;

    /**
     * Confidence level (0.0 - 1.0).
     */
    private Double confidence;

    /**
     * Token count for this thought.
     */
    private Integer tokenCount;

    /**
     * When this thought was generated.
     */
    private Instant timestamp;

    /**
     * Duration to generate this thought in milliseconds.
     */
    private Long durationMs;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    /**
     * Thought types for multi-agent execution.
     */
    public enum TeamThoughtType {
        // Task lifecycle
        TASK_RECEIVED,
        TASK_DECOMPOSING,
        TASK_DECOMPOSED,
        TASK_ASSIGNED,
        TASK_STARTED,
        TASK_PROGRESS,
        TASK_COMPLETED,
        TASK_FAILED,

        // Agent coordination
        AGENT_THINKING,
        AGENT_REASONING,
        AGENT_PLANNING,
        AGENT_EXECUTING,
        AGENT_WAITING,

        // Inter-agent communication
        MESSAGE_SENT,
        MESSAGE_RECEIVED,
        HANDOFF,
        DELEGATION,

        // Debate/consensus
        POSITION_STATED,
        ARGUMENT_MADE,
        COUNTER_ARGUMENT,
        AGREEMENT,
        DISAGREEMENT,
        CONSENSUS_REACHED,
        VOTE_CAST,

        // Review/critique
        REVIEW_STARTED,
        CRITIQUE_GIVEN,
        FEEDBACK_RECEIVED,
        REVISION_NEEDED,
        APPROVED,
        REJECTED,

        // Synthesis
        AGGREGATING,
        SYNTHESIZING,
        FINAL_OUTPUT,

        // Tool usage
        TOOL_CALL,
        TOOL_RESULT,
        TOOL_ERROR,

        // System
        ERROR,
        WARNING,
        INFO,
        DEBUG
    }

    // Factory methods for common thought types

    public static TeamThought taskReceived(String teamTaskId, String agentId, String agentName, String content) {
        return TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .agentId(agentId)
                .agentName(agentName)
                .agentRole(TeamRole.ORCHESTRATOR)
                .type(TeamThoughtType.TASK_RECEIVED)
                .content(content)
                .phase("initialization")
                .timestamp(Instant.now())
                .build();
    }

    public static TeamThought decomposing(String teamTaskId, String agentId, String agentName, int numSubtasks) {
        return TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .agentId(agentId)
                .agentName(agentName)
                .agentRole(TeamRole.ORCHESTRATOR)
                .type(TeamThoughtType.TASK_DECOMPOSING)
                .content("Decomposing task into " + numSubtasks + " subtasks")
                .phase("decomposition")
                .timestamp(Instant.now())
                .build();
    }

    public static TeamThought agentThinking(String teamTaskId, String subTaskId, String agentId,
                                             String agentName, TeamRole role, String content, int iteration) {
        return TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .subTaskId(subTaskId)
                .agentId(agentId)
                .agentName(agentName)
                .agentRole(role)
                .type(TeamThoughtType.AGENT_THINKING)
                .content(content)
                .iteration(iteration)
                .phase("execution")
                .timestamp(Instant.now())
                .build();
    }

    public static TeamThought messageSent(String teamTaskId, String agentId, String agentName,
                                           TeamRole role, String messageId, String summary) {
        return TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .agentId(agentId)
                .agentName(agentName)
                .agentRole(role)
                .type(TeamThoughtType.MESSAGE_SENT)
                .content(summary)
                .messageId(messageId)
                .phase("communication")
                .timestamp(Instant.now())
                .build();
    }

    public static TeamThought positionStated(String teamTaskId, String agentId, String agentName,
                                              String position, int round, double confidence) {
        return TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .agentId(agentId)
                .agentName(agentName)
                .type(TeamThoughtType.POSITION_STATED)
                .content(position)
                .round(round)
                .confidence(confidence)
                .phase("debate")
                .timestamp(Instant.now())
                .build();
    }

    public static TeamThought consensusReached(String teamTaskId, String content, int round, double confidence) {
        return TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .type(TeamThoughtType.CONSENSUS_REACHED)
                .content(content)
                .round(round)
                .confidence(confidence)
                .phase("consensus")
                .timestamp(Instant.now())
                .build();
    }

    public static TeamThought critiqueGiven(String teamTaskId, String subTaskId, String agentId,
                                             String agentName, String critique, double confidence) {
        return TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .subTaskId(subTaskId)
                .agentId(agentId)
                .agentName(agentName)
                .agentRole(TeamRole.CRITIC)
                .type(TeamThoughtType.CRITIQUE_GIVEN)
                .content(critique)
                .confidence(confidence)
                .phase("review")
                .timestamp(Instant.now())
                .build();
    }

    public static TeamThought synthesizing(String teamTaskId, String agentId, String agentName, String content) {
        return TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .agentId(agentId)
                .agentName(agentName)
                .agentRole(TeamRole.SYNTHESIZER)
                .type(TeamThoughtType.SYNTHESIZING)
                .content(content)
                .phase("synthesis")
                .timestamp(Instant.now())
                .build();
    }

    public static TeamThought finalOutput(String teamTaskId, String content, double confidence) {
        return TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .type(TeamThoughtType.FINAL_OUTPUT)
                .content(content)
                .confidence(confidence)
                .phase("completion")
                .timestamp(Instant.now())
                .build();
    }

    public static TeamThought error(String teamTaskId, String agentId, String errorMessage) {
        return TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .agentId(agentId)
                .type(TeamThoughtType.ERROR)
                .content(errorMessage)
                .phase("error")
                .timestamp(Instant.now())
                .build();
    }
}
