package com.z254.butterfly.cortex.domain.repository.impl;

import com.z254.butterfly.cortex.domain.model.Conversation;
import com.z254.butterfly.cortex.domain.repository.ConversationRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ConversationRepository} implementation for local development.
 */
@Repository
public class InMemoryConversationRepository implements ConversationRepository {

    private final Map<String, Conversation> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Conversation> save(Conversation conversation) {
        if (conversation.getId() == null || conversation.getId().isBlank()) {
            conversation.setId(UUID.randomUUID().toString());
        }
        Instant now = Instant.now();
        if (conversation.getCreatedAt() == null) {
            conversation.setCreatedAt(now);
        }
        conversation.setUpdatedAt(now);
        store.put(conversation.getId(), conversation);
        return Mono.just(conversation);
    }

    @Override
    public Mono<Conversation> findById(String id) {
        return Mono.justOrEmpty(store.get(id));
    }

    @Override
    public Flux<Conversation> findByUserId(String userId) {
        return Flux.fromStream(store.values().stream()
                .filter(conversation -> Objects.equals(conversation.getUserId(), userId)));
    }

    @Override
    public Flux<Conversation> findByAgentId(String agentId) {
        return Flux.fromStream(store.values().stream()
                .filter(conversation -> Objects.equals(conversation.getAgentId(), agentId)));
    }

    @Override
    public Flux<Conversation> findByUserIdAndAgentId(String userId, String agentId) {
        return Flux.fromStream(store.values().stream()
                .filter(conversation -> Objects.equals(conversation.getUserId(), userId)
                        && Objects.equals(conversation.getAgentId(), agentId)));
    }

    @Override
    public Flux<Conversation> findRecentByUserId(String userId, int limit) {
        return findByUserId(userId)
                .sort((a, b) -> b.getLastActiveAt().compareTo(a.getLastActiveAt()))
                .take(limit);
    }

    @Override
    public Flux<Conversation> findByStatus(Conversation.ConversationStatus status) {
        return Flux.fromStream(store.values().stream()
                .filter(conversation -> conversation.getStatus() == status));
    }

    @Override
    public Flux<Conversation> findByLastActiveAtAfter(Instant after) {
        return Flux.fromStream(store.values().stream()
                .filter(conversation -> conversation.getLastActiveAt() != null
                        && conversation.getLastActiveAt().isAfter(after)));
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
    public Mono<Long> archiveOlderThan(Instant before) {
        long count = store.values().stream()
                .filter(conversation -> conversation.getLastActiveAt() != null
                        && conversation.getLastActiveAt().isBefore(before))
                .peek(conversation -> conversation.setStatus(Conversation.ConversationStatus.ARCHIVED))
                .count();
        return Mono.just(count);
    }
}
