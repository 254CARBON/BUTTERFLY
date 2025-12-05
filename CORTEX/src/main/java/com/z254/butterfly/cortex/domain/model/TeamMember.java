package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an agent's membership in a team.
 * Defines the agent's role, specializations, and permissions within the team.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMember {

    /**
     * ID of the agent that is a team member.
     */
    private String agentId;

    /**
     * Role of the agent within the team.
     */
    private TeamRole role;

    /**
     * Specializations this member brings to the team.
     * These override or supplement the agent's default specialization.
     */
    @Builder.Default
    private Set<String> specializations = new HashSet<>();

    /**
     * Priority of this member (higher = more important).
     * Used for task assignment when multiple agents can handle a task.
     */
    @Builder.Default
    private int priority = 0;

    /**
     * Whether this member can delegate tasks to other members.
     */
    @Builder.Default
    private boolean canDelegate = false;

    /**
     * Whether this member can approve task outputs.
     */
    @Builder.Default
    private boolean canApprove = false;

    /**
     * Maximum number of concurrent subtasks this member can handle.
     */
    @Builder.Default
    private int maxConcurrentTasks = 3;

    /**
     * Weight of this member's vote in debate scenarios.
     */
    @Builder.Default
    private double voteWeight = 1.0;

    /**
     * Current workload (number of active subtasks).
     */
    @Builder.Default
    private int currentWorkload = 0;

    /**
     * Whether this member is currently available.
     */
    @Builder.Default
    private boolean available = true;

    /**
     * When this member joined the team.
     */
    private Instant joinedAt;

    /**
     * Last time this member was active.
     */
    private Instant lastActiveAt;

    /**
     * Total tasks completed by this member.
     */
    @Builder.Default
    private long tasksCompleted = 0;

    /**
     * Success rate of this member's tasks (0.0 - 1.0).
     */
    @Builder.Default
    private double successRate = 1.0;

    /**
     * Check if this member is the team orchestrator.
     */
    public boolean isOrchestrator() {
        return role == TeamRole.ORCHESTRATOR;
    }

    /**
     * Check if this member can accept more tasks.
     */
    public boolean canAcceptTasks() {
        return available && currentWorkload < maxConcurrentTasks;
    }

    /**
     * Check if this member has a specialization.
     */
    public boolean hasSpecialization(String specialization) {
        return specializations != null && specializations.contains(specialization.toLowerCase());
    }

    /**
     * Increment the workload counter.
     */
    public void incrementWorkload() {
        this.currentWorkload++;
        this.lastActiveAt = Instant.now();
    }

    /**
     * Decrement the workload counter.
     */
    public void decrementWorkload() {
        if (this.currentWorkload > 0) {
            this.currentWorkload--;
        }
    }

    /**
     * Record a task completion and update success rate.
     */
    public void recordTaskCompletion(boolean success) {
        tasksCompleted++;
        // Exponential moving average for success rate
        double alpha = 0.1;
        successRate = successRate * (1 - alpha) + (success ? 1.0 : 0.0) * alpha;
        lastActiveAt = Instant.now();
    }

    /**
     * Create an orchestrator member.
     */
    public static TeamMember orchestrator(String agentId) {
        return TeamMember.builder()
                .agentId(agentId)
                .role(TeamRole.ORCHESTRATOR)
                .canDelegate(true)
                .canApprove(true)
                .priority(100)
                .voteWeight(2.0)
                .maxConcurrentTasks(1) // Orchestrator focuses on coordination
                .joinedAt(Instant.now())
                .build();
    }

    /**
     * Create a specialist member.
     */
    public static TeamMember specialist(String agentId, Set<String> specializations) {
        return TeamMember.builder()
                .agentId(agentId)
                .role(TeamRole.SPECIALIST)
                .specializations(specializations)
                .canDelegate(false)
                .canApprove(false)
                .priority(50)
                .voteWeight(1.0)
                .maxConcurrentTasks(3)
                .joinedAt(Instant.now())
                .build();
    }

    /**
     * Create a critic member.
     */
    public static TeamMember critic(String agentId) {
        return TeamMember.builder()
                .agentId(agentId)
                .role(TeamRole.CRITIC)
                .specializations(Set.of("review", "validation"))
                .canDelegate(false)
                .canApprove(true)
                .priority(75)
                .voteWeight(1.5)
                .maxConcurrentTasks(5) // Critics can review multiple outputs
                .joinedAt(Instant.now())
                .build();
    }

    /**
     * Create a synthesizer member.
     */
    public static TeamMember synthesizer(String agentId) {
        return TeamMember.builder()
                .agentId(agentId)
                .role(TeamRole.SYNTHESIZER)
                .specializations(Set.of("synthesis", "summarization"))
                .canDelegate(false)
                .canApprove(false)
                .priority(60)
                .voteWeight(1.0)
                .maxConcurrentTasks(2)
                .joinedAt(Instant.now())
                .build();
    }
}
