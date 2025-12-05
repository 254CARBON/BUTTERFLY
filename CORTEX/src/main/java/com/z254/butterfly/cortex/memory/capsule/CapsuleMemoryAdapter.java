package com.z254.butterfly.cortex.memory.capsule;

import com.z254.butterfly.cortex.client.CapsuleClient;
import com.z254.butterfly.cortex.client.CapsuleClient.ConversationSnapshot;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.Conversation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter that bridges agent memory to CAPSULE for persistent storage.
 * Handles conversation snapshots, historical context retrieval, and sync.
 */
@Component
@Slf4j
public class CapsuleMemoryAdapter {

    private final CapsuleClient capsuleClient;
    private final CortexProperties.MemoryProperties.CapsuleProperties config;
    
    // Track conversations that need syncing
    private final Map<String, Conversation> pendingSync = new ConcurrentHashMap<>();

    public CapsuleMemoryAdapter(
            CapsuleClient capsuleClient,
            CortexProperties cortexProperties) {
        this.capsuleClient = capsuleClient;
        this.config = cortexProperties.getMemory().getCapsule();
    }

    /**
     * Store a conversation snapshot in CAPSULE.
     *
     * @param conversation the conversation to snapshot
     * @return the CAPSULE ID
     */
    public Mono<String> storeConversation(Conversation conversation) {
        if (!config.isEnabled()) {
            return Mono.just("disabled");
        }

        ConversationSnapshot snapshot = ConversationSnapshot.fromConversation(conversation);
        
        return capsuleClient.storeSnapshot(snapshot)
                .doOnSuccess(id -> {
                    conversation.setCapsuleId(id);
                    log.debug("Stored conversation {} in CAPSULE: {}", conversation.getId(), id);
                })
                .onErrorResume(e -> {
                    log.warn("Failed to store conversation in CAPSULE: {}. Queuing for retry.", 
                            e.getMessage());
                    pendingSync.put(conversation.getId(), conversation);
                    return Mono.just("pending-" + conversation.getId());
                });
    }

    /**
     * Retrieve historical conversation context.
     *
     * @param agentId the agent ID
     * @param userId the user ID
     * @param limit maximum conversations
     * @return flux of past conversation snapshots
     */
    public Flux<ConversationSnapshot> getHistoricalContext(String agentId, String userId, int limit) {
        if (!config.isEnabled()) {
            return Flux.empty();
        }

        return capsuleClient.getHistory(agentId, userId, limit)
                .doOnError(e -> log.warn("Failed to retrieve historical context: {}", e.getMessage()))
                .onErrorResume(e -> Flux.empty());
    }

    /**
     * Get conversation state at a specific point in time.
     *
     * @param conversationId the conversation ID
     * @param timestamp the point in time
     * @return the snapshot at that time
     */
    public Mono<ConversationSnapshot> getConversationAtTime(String conversationId, Instant timestamp) {
        if (!config.isEnabled()) {
            return Mono.empty();
        }

        return capsuleClient.getAtTime(conversationId, timestamp)
                .doOnError(e -> log.warn("Failed time-travel query: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Retrieve context related to a RIM node.
     *
     * @param rimNodeId the RIM node ID
     * @param limit maximum results
     * @return flux of related conversation snapshots
     */
    public Flux<ConversationSnapshot> getContextByRimNode(String rimNodeId, int limit) {
        if (!config.isEnabled()) {
            return Flux.empty();
        }

        return capsuleClient.queryByRimNode(rimNodeId, limit)
                .doOnError(e -> log.warn("Failed to query by RIM node: {}", e.getMessage()))
                .onErrorResume(e -> Flux.empty());
    }

    /**
     * Mark a conversation for sync to CAPSULE.
     *
     * @param conversation the conversation to sync
     */
    public void markForSync(Conversation conversation) {
        if (config.isEnabled() && config.isSyncOnComplete()) {
            pendingSync.put(conversation.getId(), conversation);
        }
    }

    /**
     * Sync a specific conversation to CAPSULE.
     *
     * @param conversation the conversation
     * @return completion signal
     */
    public Mono<Void> syncConversation(Conversation conversation) {
        if (!config.isEnabled()) {
            return Mono.empty();
        }

        if (conversation.getCapsuleId() != null && !conversation.getCapsuleId().startsWith("pending")) {
            // Update existing
            ConversationSnapshot snapshot = ConversationSnapshot.fromConversation(conversation);
            return capsuleClient.updateSnapshot(conversation.getCapsuleId(), snapshot)
                    .doOnSuccess(v -> {
                        pendingSync.remove(conversation.getId());
                        log.debug("Updated conversation {} in CAPSULE", conversation.getId());
                    });
        } else {
            // Create new
            return storeConversation(conversation).then();
        }
    }

    /**
     * Build context from historical conversations for new task.
     *
     * @param agentId the agent ID
     * @param userId the user ID
     * @param maxSummaries maximum summaries to include
     * @return context string
     */
    public Mono<String> buildHistoricalContext(String agentId, String userId, int maxSummaries) {
        return getHistoricalContext(agentId, userId, maxSummaries)
                .map(snapshot -> String.format(
                        "Previous conversation (%s): %s",
                        snapshot.snapshotAt(),
                        snapshot.summary() != null ? snapshot.summary() : "No summary"
                ))
                .collectList()
                .map(summaries -> {
                    if (summaries.isEmpty()) {
                        return "";
                    }
                    return "Historical context:\n" + String.join("\n", summaries);
                });
    }

    /**
     * Scheduled task to sync pending conversations.
     */
    @Scheduled(fixedRateString = "${cortex.memory.capsule.snapshot-interval:PT5M}")
    public void scheduledSync() {
        if (!config.isEnabled() || pendingSync.isEmpty()) {
            return;
        }

        log.debug("Starting CAPSULE sync for {} pending conversations", pendingSync.size());

        Flux.fromIterable(pendingSync.values())
                .flatMap(this::syncConversation)
                .then()
                .subscribe(
                        v -> log.debug("CAPSULE sync complete"),
                        e -> log.error("CAPSULE sync failed: {}", e.getMessage())
                );
    }

    /**
     * Check if CAPSULE integration is available.
     *
     * @return true if available
     */
    public Mono<Boolean> isAvailable() {
        if (!config.isEnabled()) {
            return Mono.just(false);
        }
        return capsuleClient.isAvailable();
    }
}
