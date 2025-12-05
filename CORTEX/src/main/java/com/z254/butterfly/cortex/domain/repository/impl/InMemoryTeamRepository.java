package com.z254.butterfly.cortex.domain.repository.impl;

import com.z254.butterfly.cortex.domain.model.AgentTeam;
import com.z254.butterfly.cortex.domain.model.TeamStatus;
import com.z254.butterfly.cortex.domain.repository.TeamRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of TeamRepository for development and testing.
 */
@Repository
public class InMemoryTeamRepository implements TeamRepository {

    private final Map<String, AgentTeam> teams = new ConcurrentHashMap<>();

    @Override
    public Mono<AgentTeam> save(AgentTeam team) {
        if (team.getId() == null) {
            team.setId(UUID.randomUUID().toString());
        }
        if (team.getCreatedAt() == null) {
            team.setCreatedAt(Instant.now());
        }
        team.setUpdatedAt(Instant.now());
        teams.put(team.getId(), team);
        return Mono.just(team);
    }

    @Override
    public Mono<AgentTeam> findById(String teamId) {
        return Mono.justOrEmpty(teams.get(teamId));
    }

    @Override
    public Flux<AgentTeam> findAll() {
        return Flux.fromIterable(teams.values());
    }

    @Override
    public Flux<AgentTeam> findByNamespace(String namespace) {
        return Flux.fromIterable(teams.values())
                .filter(team -> namespace.equals(team.getNamespace()));
    }

    @Override
    public Flux<AgentTeam> findByStatus(TeamStatus status) {
        return Flux.fromIterable(teams.values())
                .filter(team -> status == team.getStatus());
    }

    @Override
    public Flux<AgentTeam> findByOwner(String owner) {
        return Flux.fromIterable(teams.values())
                .filter(team -> owner.equals(team.getOwner()));
    }

    @Override
    public Flux<AgentTeam> findByMemberAgentId(String agentId) {
        return Flux.fromIterable(teams.values())
                .filter(team -> team.getMembers() != null &&
                        team.getMembers().stream()
                                .anyMatch(member -> agentId.equals(member.getAgentId())));
    }

    @Override
    public Mono<Void> deleteById(String teamId) {
        teams.remove(teamId);
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> existsById(String teamId) {
        return Mono.just(teams.containsKey(teamId));
    }
}
