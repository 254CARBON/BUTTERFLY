package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a team of coordinated AI agents.
 * Teams enable multi-agent orchestration with different coordination patterns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTeam {

    /**
     * Unique identifier for the team.
     */
    private String id;

    /**
     * Display name of the team.
     */
    private String name;

    /**
     * Description of the team's purpose and capabilities.
     */
    private String description;

    /**
     * Type of team (determines coordination pattern).
     */
    @Builder.Default
    private TeamType teamType = TeamType.HIERARCHICAL;

    /**
     * ID of the agent acting as orchestrator.
     * Required for HIERARCHICAL and COLLABORATIVE teams.
     */
    private String orchestratorAgentId;

    /**
     * Members of the team.
     */
    @Builder.Default
    private List<TeamMember> members = new ArrayList<>();

    /**
     * Coordination configuration.
     */
    @Builder.Default
    private CoordinationConfig coordinationConfig = CoordinationConfig.hierarchical();

    /**
     * Current status of the team.
     */
    @Builder.Default
    private TeamStatus status = TeamStatus.DRAFT;

    /**
     * Namespace for multi-tenancy.
     */
    @Builder.Default
    private String namespace = "default";

    /**
     * Owner of the team.
     */
    private String owner;

    /**
     * Tags for categorization.
     */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /**
     * When the team was created.
     */
    private Instant createdAt;

    /**
     * When the team was last updated.
     */
    private Instant updatedAt;

    /**
     * When the team was last active.
     */
    private Instant lastActiveAt;

    /**
     * Total tasks executed by this team.
     */
    @Builder.Default
    private long totalTasks = 0;

    /**
     * Successful tasks.
     */
    @Builder.Default
    private long successfulTasks = 0;

    /**
     * Average task duration in milliseconds.
     */
    @Builder.Default
    private double avgTaskDurationMs = 0;

    /**
     * Additional metadata.
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Get the number of team members.
     */
    public int getMemberCount() {
        return members != null ? members.size() : 0;
    }

    /**
     * Find the orchestrator member.
     */
    public Optional<TeamMember> getOrchestrator() {
        if (members == null) {
            return Optional.empty();
        }
        return members.stream()
                .filter(m -> m.getRole() == TeamRole.ORCHESTRATOR)
                .findFirst();
    }

    /**
     * Find members by role.
     */
    public List<TeamMember> getMembersByRole(TeamRole role) {
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .filter(m -> m.getRole() == role)
                .toList();
    }

    /**
     * Find members with a specific specialization.
     */
    public List<TeamMember> getMembersWithSpecialization(String specialization) {
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .filter(m -> m.hasSpecialization(specialization))
                .toList();
    }

    /**
     * Find available members.
     */
    public List<TeamMember> getAvailableMembers() {
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .filter(TeamMember::canAcceptTasks)
                .toList();
    }

    /**
     * Find a member by agent ID.
     */
    public Optional<TeamMember> getMember(String agentId) {
        if (members == null) {
            return Optional.empty();
        }
        return members.stream()
                .filter(m -> m.getAgentId().equals(agentId))
                .findFirst();
    }

    /**
     * Add a member to the team.
     */
    public void addMember(TeamMember member) {
        if (members == null) {
            members = new ArrayList<>();
        }
        
        // Ensure only one orchestrator
        if (member.isOrchestrator()) {
            members.removeIf(TeamMember::isOrchestrator);
            this.orchestratorAgentId = member.getAgentId();
        }
        
        // Remove existing membership for this agent
        members.removeIf(m -> m.getAgentId().equals(member.getAgentId()));
        members.add(member);
        
        if (member.getJoinedAt() == null) {
            member.setJoinedAt(Instant.now());
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Remove a member from the team.
     */
    public boolean removeMember(String agentId) {
        if (members == null) {
            return false;
        }
        
        boolean removed = members.removeIf(m -> m.getAgentId().equals(agentId));
        
        if (removed && agentId.equals(orchestratorAgentId)) {
            // If we removed the orchestrator, clear the orchestrator ID
            this.orchestratorAgentId = null;
            // Optionally promote the highest priority member
            members.stream()
                    .max((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                    .ifPresent(newOrch -> {
                        newOrch.setRole(TeamRole.ORCHESTRATOR);
                        this.orchestratorAgentId = newOrch.getAgentId();
                    });
        }
        
        this.updatedAt = Instant.now();
        return removed;
    }

    /**
     * Check if the team is ready to accept tasks.
     */
    public boolean isReady() {
        if (status != TeamStatus.ACTIVE) {
            return false;
        }
        if (members == null || members.isEmpty()) {
            return false;
        }
        // Hierarchical teams need an orchestrator
        if (teamType == TeamType.HIERARCHICAL && orchestratorAgentId == null) {
            return false;
        }
        return true;
    }

    /**
     * Record a task completion.
     */
    public void recordTaskCompletion(boolean success, long durationMs) {
        totalTasks++;
        if (success) {
            successfulTasks++;
        }
        // Exponential moving average for duration
        double alpha = 0.1;
        avgTaskDurationMs = avgTaskDurationMs * (1 - alpha) + durationMs * alpha;
        lastActiveAt = Instant.now();
    }

    /**
     * Get success rate.
     */
    public double getSuccessRate() {
        if (totalTasks == 0) {
            return 0.0;
        }
        return (double) successfulTasks / totalTasks;
    }

    /**
     * Activate the team.
     */
    public void activate() {
        if (isReady()) {
            this.status = TeamStatus.ACTIVE;
            this.updatedAt = Instant.now();
        } else {
            throw new IllegalStateException("Team is not ready to be activated");
        }
    }

    /**
     * Pause the team.
     */
    public void pause() {
        this.status = TeamStatus.PAUSED;
        this.updatedAt = Instant.now();
    }

    /**
     * Archive the team.
     */
    public void archive() {
        this.status = TeamStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }

    /**
     * Create a new hierarchical team with an orchestrator.
     */
    public static AgentTeam hierarchical(String name, String orchestratorAgentId) {
        TeamMember orchestrator = TeamMember.orchestrator(orchestratorAgentId);
        return AgentTeam.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .teamType(TeamType.HIERARCHICAL)
                .orchestratorAgentId(orchestratorAgentId)
                .members(new ArrayList<>(List.of(orchestrator)))
                .coordinationConfig(CoordinationConfig.hierarchical())
                .status(TeamStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Create a new debate team.
     */
    public static AgentTeam debate(String name, List<String> agentIds) {
        List<TeamMember> members = agentIds.stream()
                .map(id -> TeamMember.builder()
                        .agentId(id)
                        .role(TeamRole.SPECIALIST)
                        .voteWeight(1.0)
                        .joinedAt(Instant.now())
                        .build())
                .toList();
        
        return AgentTeam.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .teamType(TeamType.DEBATE)
                .members(new ArrayList<>(members))
                .coordinationConfig(CoordinationConfig.debate())
                .status(TeamStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Create a new parallel team.
     */
    public static AgentTeam parallel(String name, List<String> agentIds) {
        List<TeamMember> members = agentIds.stream()
                .map(id -> TeamMember.builder()
                        .agentId(id)
                        .role(TeamRole.WORKER)
                        .joinedAt(Instant.now())
                        .build())
                .toList();
        
        return AgentTeam.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .teamType(TeamType.PARALLEL)
                .members(new ArrayList<>(members))
                .coordinationConfig(CoordinationConfig.parallel())
                .status(TeamStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
