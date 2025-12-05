package com.z254.butterfly.cortex.domain.repository;

import com.z254.butterfly.cortex.domain.model.AgentTeam;
import com.z254.butterfly.cortex.domain.model.TeamStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository interface for AgentTeam persistence.
 */
public interface TeamRepository {

    /**
     * Save a team.
     */
    Mono<AgentTeam> save(AgentTeam team);

    /**
     * Find a team by ID.
     */
    Mono<AgentTeam> findById(String teamId);

    /**
     * Find all teams.
     */
    Flux<AgentTeam> findAll();

    /**
     * Find teams by namespace.
     */
    Flux<AgentTeam> findByNamespace(String namespace);

    /**
     * Find teams by status.
     */
    Flux<AgentTeam> findByStatus(TeamStatus status);

    /**
     * Find teams by owner.
     */
    Flux<AgentTeam> findByOwner(String owner);

    /**
     * Find teams containing a specific agent.
     */
    Flux<AgentTeam> findByMemberAgentId(String agentId);

    /**
     * Delete a team by ID.
     */
    Mono<Void> deleteById(String teamId);

    /**
     * Check if a team exists.
     */
    Mono<Boolean> existsById(String teamId);
}
