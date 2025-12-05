package com.z254.butterfly.cortex.orchestration;

import com.z254.butterfly.cortex.domain.model.AgentTeam;
import com.z254.butterfly.cortex.domain.model.TeamResult;
import com.z254.butterfly.cortex.domain.model.TeamTask;
import com.z254.butterfly.cortex.domain.model.TeamThought;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Core interface for multi-agent orchestration.
 * Provides team management and coordinated task execution capabilities.
 */
public interface MultiAgentOrchestrator {

    // --------------------------------------------------------------------------------------------
    // Team Management
    // --------------------------------------------------------------------------------------------

    /**
     * Create a new agent team.
     *
     * @param team The team definition
     * @return The created team with generated ID
     */
    Mono<AgentTeam> createTeam(AgentTeam team);

    /**
     * Get a team by ID.
     *
     * @param teamId The team ID
     * @return The team, or empty if not found
     */
    Mono<AgentTeam> getTeam(String teamId);

    /**
     * Update an existing team.
     *
     * @param teamId The team ID
     * @param team   The updated team definition
     * @return The updated team
     */
    Mono<AgentTeam> updateTeam(String teamId, AgentTeam team);

    /**
     * Delete a team.
     *
     * @param teamId The team ID
     * @return Void on completion
     */
    Mono<Void> deleteTeam(String teamId);

    /**
     * List all teams.
     *
     * @return Stream of teams
     */
    Flux<AgentTeam> listTeams();

    /**
     * List teams in a namespace.
     *
     * @param namespace The namespace
     * @return Stream of teams
     */
    Flux<AgentTeam> listTeamsByNamespace(String namespace);

    /**
     * Activate a team (make it ready to accept tasks).
     *
     * @param teamId The team ID
     * @return The activated team
     */
    Mono<AgentTeam> activateTeam(String teamId);

    /**
     * Pause a team.
     *
     * @param teamId The team ID
     * @return The paused team
     */
    Mono<AgentTeam> pauseTeam(String teamId);

    // --------------------------------------------------------------------------------------------
    // Task Execution
    // --------------------------------------------------------------------------------------------

    /**
     * Submit a task for team execution.
     *
     * @param task The task to submit
     * @return The submitted task with generated ID
     */
    Mono<TeamTask> submitTask(TeamTask task);

    /**
     * Execute a team task with streaming thought output.
     *
     * @param task The task to execute
     * @return Stream of thoughts from all agents in the team
     */
    Flux<TeamThought> executeTaskStreaming(TeamTask task);

    /**
     * Execute a team task and return the final result.
     *
     * @param task The task to execute
     * @return The aggregated result from the team
     */
    Mono<TeamResult> executeTask(TeamTask task);

    /**
     * Get a task by ID.
     *
     * @param taskId The task ID
     * @return The task, or empty if not found
     */
    Mono<TeamTask> getTask(String taskId);

    /**
     * Get the result of a task.
     *
     * @param taskId The task ID
     * @return The result, or empty if not available
     */
    Mono<TeamResult> getTaskResult(String taskId);

    /**
     * Cancel a running task.
     *
     * @param taskId The task ID
     * @param reason The cancellation reason
     * @return The cancelled task
     */
    Mono<TeamTask> cancelTask(String taskId, String reason);

    /**
     * List tasks for a team.
     *
     * @param teamId The team ID
     * @return Stream of tasks
     */
    Flux<TeamTask> listTasksForTeam(String teamId);
}
