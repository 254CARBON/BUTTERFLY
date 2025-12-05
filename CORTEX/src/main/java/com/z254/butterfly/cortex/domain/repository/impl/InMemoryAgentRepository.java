package com.z254.butterfly.cortex.domain.repository.impl;

import com.z254.butterfly.cortex.domain.model.Agent;
import com.z254.butterfly.cortex.domain.repository.AgentRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory implementation of {@link AgentRepository}.
 * Provides a development-time persistence mechanism until a real data store (e.g. Cassandra) is wired in.
 */
@Repository
public class InMemoryAgentRepository implements AgentRepository {

    private final Map<String, Agent> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Agent> save(Agent agent) {
        if (agent.getId() == null || agent.getId().isBlank()) {
            agent.setId(UUID.randomUUID().toString());
        }
        Instant now = Instant.now();
        if (agent.getCreatedAt() == null) {
            agent.setCreatedAt(now);
        }
        agent.setUpdatedAt(now);
        store.put(agent.getId(), agent);
        return Mono.just(agent);
    }

    @Override
    public Mono<Agent> findById(String id) {
        return Mono.justOrEmpty(store.get(id));
    }

    @Override
    public Flux<Agent> findAll() {
        return Flux.fromIterable(store.values());
    }

    @Override
    public Flux<Agent> findByNamespace(String namespace) {
        return Flux.fromStream(store.values().stream()
                .filter(agent -> Objects.equals(agent.getNamespace(), namespace)));
    }

    @Override
    public Flux<Agent> findByOwner(String owner) {
        return Flux.fromStream(store.values().stream()
                .filter(agent -> Objects.equals(agent.getOwner(), owner)));
    }

    @Override
    public Flux<Agent> findByStatus(Agent.AgentStatus status) {
        return Flux.fromStream(store.values().stream()
                .filter(agent -> agent.getStatus() == status));
    }

    @Override
    public Mono<Void> deleteById(String id) {
        store.remove(id);
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> existsById(String id) {
        return Mono.just(store.containsKey(id));
    }

    @Override
    public Mono<Agent> updateStatistics(String id, boolean success) {
        Agent agent = store.get(id);
        if (agent == null) {
            return Mono.empty();
        }
        long count = agent.getTaskCount() != null ? agent.getTaskCount() + 1 : 1;
        double successRate = agent.getSuccessRate() != null ? agent.getSuccessRate() : 1.0;
        if (success) {
            successRate = ((successRate * (count - 1)) + 1.0) / count;
        } else {
            successRate = ((successRate * (count - 1))) / count;
        }
        agent.setTaskCount(count);
        agent.setSuccessRate(successRate);
        agent.setLastUsedAt(Instant.now());
        store.put(id, agent);
        return Mono.just(agent);
    }
}
