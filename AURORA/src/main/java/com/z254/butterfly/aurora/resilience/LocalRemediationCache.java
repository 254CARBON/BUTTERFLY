package com.z254.butterfly.aurora.resilience;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.remediation.RemediationPlaybook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Local cache for time-critical remediation paths.
 * <p>
 * Provides:
 * <ul>
 *     <li>Cached playbooks for quick access</li>
 *     <li>Cached approval decisions</li>
 *     <li>Action queue for retry</li>
 *     <li>Evidence buffer for local storage</li>
 * </ul>
 */
@Slf4j
@Component
public class LocalRemediationCache {

    private final AuroraProperties auroraProperties;

    // Playbook cache
    private final Cache<String, RemediationPlaybook.Playbook> playbookCache;

    // Approval cache
    private final Cache<String, FallbackChains.CachedApproval> approvalCache;

    // Action queue for retry
    private final Queue<FallbackChains.QueuedAction> actionQueue = new ConcurrentLinkedQueue<>();

    // Evidence buffer
    private final Queue<BufferedEvidence> evidenceBuffer = new ConcurrentLinkedQueue<>();

    public LocalRemediationCache(AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;

        Duration playbookTtl = auroraProperties.getRemediation().getPlaybookCacheTtl();
        Duration approvalTtl = auroraProperties.getPlato().getCache().getApprovalTtl();

        this.playbookCache = Caffeine.newBuilder()
                .expireAfterWrite(playbookTtl)
                .maximumSize(100)
                .recordStats()
                .build();

        this.approvalCache = Caffeine.newBuilder()
                .expireAfterWrite(approvalTtl)
                .maximumSize(1000)
                .recordStats()
                .build();

        log.info("Initialized local remediation cache: playbookTtl={}, approvalTtl={}",
                playbookTtl, approvalTtl);
    }

    // ========== Playbook Cache ==========

    /**
     * Cache a playbook.
     */
    public void cachePlaybook(String actionType, RemediationPlaybook.Playbook playbook) {
        playbookCache.put(actionType, playbook);
    }

    /**
     * Get cached playbook.
     */
    public Optional<RemediationPlaybook.Playbook> getCachedPlaybook(String actionType) {
        return Optional.ofNullable(playbookCache.getIfPresent(actionType));
    }

    // ========== Approval Cache ==========

    /**
     * Cache an approval decision.
     */
    public void cacheApproval(String component, String actionType, 
                               FallbackChains.CachedApproval approval) {
        String key = buildApprovalKey(component, actionType);
        approvalCache.put(key, approval);
    }

    /**
     * Get cached approval.
     */
    public Optional<FallbackChains.CachedApproval> getCachedApproval(String component, 
                                                                       String actionType) {
        String key = buildApprovalKey(component, actionType);
        FallbackChains.CachedApproval cached = approvalCache.getIfPresent(key);
        
        if (cached != null && cached.getExpiresAt() != null && 
                Instant.now().isAfter(cached.getExpiresAt())) {
            approvalCache.invalidate(key);
            return Optional.empty();
        }
        
        return Optional.ofNullable(cached);
    }

    // ========== Action Queue ==========

    /**
     * Queue an action for retry.
     */
    public void queueAction(FallbackChains.QueuedAction action) {
        if (actionQueue.size() >= 1000) {
            log.warn("Action queue full, dropping oldest action");
            actionQueue.poll();
        }
        actionQueue.add(action);
    }

    /**
     * Get next queued action.
     */
    public Optional<FallbackChains.QueuedAction> pollQueuedAction() {
        return Optional.ofNullable(actionQueue.poll());
    }

    /**
     * Get queue size.
     */
    public int getQueueSize() {
        return actionQueue.size();
    }

    /**
     * Get all queued actions (for retry processing).
     */
    public List<FallbackChains.QueuedAction> getAllQueuedActions() {
        return new ArrayList<>(actionQueue);
    }

    // ========== Evidence Buffer ==========

    /**
     * Buffer evidence for later storage.
     */
    public void bufferEvidence(BufferedEvidence evidence) {
        int maxSize = auroraProperties.getCapsule().getLocalBufferSize();
        if (evidenceBuffer.size() >= maxSize) {
            log.warn("Evidence buffer full, dropping oldest evidence");
            evidenceBuffer.poll();
        }
        evidenceBuffer.add(evidence);
    }

    /**
     * Get buffered evidence.
     */
    public List<BufferedEvidence> getBufferedEvidence() {
        return new ArrayList<>(evidenceBuffer);
    }

    /**
     * Clear buffered evidence (after successful storage).
     */
    public void clearBufferedEvidence(List<String> evidenceIds) {
        evidenceBuffer.removeIf(e -> evidenceIds.contains(e.getEvidenceId()));
    }

    // ========== Stats ==========

    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        return CacheStats.builder()
                .playbookCacheSize(playbookCache.estimatedSize())
                .playbookCacheHitRate(playbookCache.stats().hitRate())
                .approvalCacheSize(approvalCache.estimatedSize())
                .approvalCacheHitRate(approvalCache.stats().hitRate())
                .actionQueueSize(actionQueue.size())
                .evidenceBufferSize(evidenceBuffer.size())
                .build();
    }

    // ========== Maintenance ==========

    /**
     * Periodic cleanup of stale entries.
     */
    @Scheduled(fixedDelayString = "${aurora.cache.cleanup-interval:60000}")
    public void cleanup() {
        playbookCache.cleanUp();
        approvalCache.cleanUp();
        
        // Clean old queued actions (older than 1 hour)
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        actionQueue.removeIf(action -> action.getQueuedAt().isBefore(cutoff));
        
        log.debug("Cache cleanup completed: playbooks={}, approvals={}, queue={}, buffer={}",
                playbookCache.estimatedSize(),
                approvalCache.estimatedSize(),
                actionQueue.size(),
                evidenceBuffer.size());
    }

    // ========== Private Methods ==========

    private String buildApprovalKey(String component, String actionType) {
        return component + ":" + actionType;
    }

    // ========== Data Classes ==========

    @lombok.Data
    @lombok.Builder
    public static class BufferedEvidence {
        private String evidenceId;
        private String incidentId;
        private String evidenceType;
        private Object data;
        private Instant bufferedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class CacheStats {
        private long playbookCacheSize;
        private double playbookCacheHitRate;
        private long approvalCacheSize;
        private double approvalCacheHitRate;
        private int actionQueueSize;
        private int evidenceBufferSize;
    }
}
