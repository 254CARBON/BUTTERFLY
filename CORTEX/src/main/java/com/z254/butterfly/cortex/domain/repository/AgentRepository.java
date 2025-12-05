package com.z254.butterfly.cortex.domain.repository;

import com.z254.butterfly.cortex.domain.model.Agent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository interface for Agent persistence.
 */
public interface AgentRepository {

    /**
     * Save an agent.
     *
     * @param agent the agent to save
     * @return the saved agent
     */
    Mono<Agent> save(Agent agent);

    /**
     * Find an agent by ID.
     *
     * @param id the agent ID
     * @return the agent or empty
     */
    Mono<Agent> findById(String id);

    /**
     * Find all agents.
     *
     * @return all agents
     */
    Flux<Agent> findAll();

    /**
     * Find agents by namespace.
     *
     * @param namespace the namespace
     * @return agents in the namespace
     */
    Flux<Agent> findByNamespace(String namespace);

    /**
     * Find agents by owner.
     *
     * @param owner the owner
     * @return agents owned by the user
     */
    Flux<Agent> findByOwner(String owner);

    /**
     * Find active agents.
     *
     * @return active agents
     */
    Flux<Agent> findByStatus(Agent.AgentStatus status);

    /**
     * Delete an agent.
     *
     * @param id the agent ID
     * @return completion signal
     */
    Mono<Void> deleteById(String id);

    /**
     * Check if an agent exists.
     *
     * @param id the agent ID
     * @return true if exists
     */
    Mono<Boolean> existsById(String id);

    /**
     * Update agent statistics after task execution.
     *
     * @param id the agent ID
     * @param success whether the task succeeded
     * @return the updated agent
     */
    Mono<Agent> updateStatistics(String id, boolean success);
}
