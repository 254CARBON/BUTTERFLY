package com.z254.butterfly.aurora.immunity;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.ImmunityRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for immunity rules (would be backed by Cassandra in production).
 * <p>
 * Provides:
 * <ul>
 *     <li>Rule storage and retrieval</li>
 *     <li>Component-based querying</li>
 *     <li>Effectiveness tracking</li>
 *     <li>Rule expiration</li>
 * </ul>
 */
@Slf4j
@Component
public class ChaosMemory {

    private final AuroraProperties auroraProperties;

    // In-memory storage (replace with Cassandra in production)
    private final Map<String, ImmunityRule> rulesById = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> rulesByComponent = new ConcurrentHashMap<>();

    public ChaosMemory(AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;
    }

    /**
     * Store an immunity rule.
     */
    public Mono<ImmunityRule> storeRule(ImmunityRule rule) {
        return Mono.fromCallable(() -> {
            log.debug("Storing immunity rule: {} for component: {}", 
                    rule.getRuleId(), rule.getComponent());

            // Check max rules per component
            Set<String> componentRules = rulesByComponent.computeIfAbsent(
                    rule.getComponent(), k -> ConcurrentHashMap.newKeySet());
            
            int maxRules = auroraProperties.getImmunity().getMaxRulesPerComponent();
            if (componentRules.size() >= maxRules) {
                // Remove oldest rule
                removeOldestRule(rule.getComponent());
            }

            rulesById.put(rule.getRuleId(), rule);
            componentRules.add(rule.getRuleId());

            return rule;
        });
    }

    /**
     * Find rules for a component.
     */
    public Flux<ImmunityRule> findRulesForComponent(String componentId) {
        return Flux.defer(() -> {
            Set<String> ruleIds = rulesByComponent.getOrDefault(componentId, Collections.emptySet());
            return Flux.fromIterable(ruleIds)
                    .map(rulesById::get)
                    .filter(Objects::nonNull)
                    .filter(this::isRuleValid);
        });
    }

    /**
     * Find a rule by ID.
     */
    public Mono<ImmunityRule> findRule(String ruleId) {
        return Mono.justOrEmpty(rulesById.get(ruleId));
    }

    /**
     * Find rules by pattern type.
     */
    public Flux<ImmunityRule> findRulesByType(String patternType) {
        return Flux.fromIterable(rulesById.values())
                .filter(rule -> rule.getPatternType().equals(patternType))
                .filter(this::isRuleValid);
    }

    /**
     * Update rule effectiveness.
     */
    public Mono<Void> updateRuleEffectiveness(String ruleId, boolean wasEffective) {
        return Mono.fromRunnable(() -> {
            ImmunityRule rule = rulesById.get(ruleId);
            if (rule != null) {
                rule.setAppliedCount(rule.getAppliedCount() + 1);
                if (wasEffective) {
                    rule.setSuccessCount(rule.getSuccessCount() + 1);
                }
                rule.setLastAppliedAt(Instant.now());
                
                log.debug("Updated effectiveness for rule {}: applied={}, success={}",
                        ruleId, rule.getAppliedCount(), rule.getSuccessCount());
            }
        });
    }

    /**
     * Increment applied count.
     */
    public Mono<Void> incrementAppliedCount(String ruleId) {
        return Mono.fromRunnable(() -> {
            ImmunityRule rule = rulesById.get(ruleId);
            if (rule != null) {
                rule.setAppliedCount(rule.getAppliedCount() + 1);
                rule.setLastAppliedAt(Instant.now());
            }
        });
    }

    /**
     * Deactivate a rule.
     */
    public Mono<Void> deactivateRule(String ruleId) {
        return Mono.fromRunnable(() -> {
            ImmunityRule rule = rulesById.get(ruleId);
            if (rule != null) {
                rule.setActive(false);
                log.info("Deactivated immunity rule: {}", ruleId);
            }
        });
    }

    /**
     * Delete a rule.
     */
    public Mono<Void> deleteRule(String ruleId) {
        return Mono.fromRunnable(() -> {
            ImmunityRule rule = rulesById.remove(ruleId);
            if (rule != null) {
                Set<String> componentRules = rulesByComponent.get(rule.getComponent());
                if (componentRules != null) {
                    componentRules.remove(ruleId);
                }
                log.info("Deleted immunity rule: {}", ruleId);
            }
        });
    }

    /**
     * Get all rules.
     */
    public Flux<ImmunityRule> getAllRules() {
        return Flux.fromIterable(rulesById.values());
    }

    /**
     * Get statistics.
     */
    public MemoryStats getStats() {
        long totalRules = rulesById.size();
        long activeRules = rulesById.values().stream()
                .filter(ImmunityRule::isActive)
                .filter(this::isRuleValid)
                .count();
        long expiredRules = rulesById.values().stream()
                .filter(r -> !isRuleValid(r))
                .count();

        return MemoryStats.builder()
                .totalRules(totalRules)
                .activeRules(activeRules)
                .expiredRules(expiredRules)
                .componentsTracked(rulesByComponent.size())
                .build();
    }

    /**
     * Cleanup expired rules.
     */
    public Mono<Integer> cleanupExpiredRules() {
        return Mono.fromCallable(() -> {
            List<String> expiredIds = rulesById.values().stream()
                    .filter(r -> !isRuleValid(r))
                    .map(ImmunityRule::getRuleId)
                    .toList();

            for (String ruleId : expiredIds) {
                deleteRule(ruleId).subscribe();
            }

            log.info("Cleaned up {} expired immunity rules", expiredIds.size());
            return expiredIds.size();
        });
    }

    // ========== Private Methods ==========

    private boolean isRuleValid(ImmunityRule rule) {
        if (!rule.isActive()) {
            return false;
        }
        
        Instant now = Instant.now();
        if (rule.getValidUntil() != null && now.isAfter(rule.getValidUntil())) {
            return false;
        }
        
        return !now.isBefore(rule.getValidFrom());
    }

    private void removeOldestRule(String component) {
        Set<String> ruleIds = rulesByComponent.get(component);
        if (ruleIds == null || ruleIds.isEmpty()) return;

        // Find oldest rule
        String oldestRuleId = ruleIds.stream()
                .map(rulesById::get)
                .filter(Objects::nonNull)
                .min(Comparator.comparing(ImmunityRule::getCreatedAt))
                .map(ImmunityRule::getRuleId)
                .orElse(null);

        if (oldestRuleId != null) {
            deleteRule(oldestRuleId).subscribe();
            log.debug("Removed oldest rule {} to make room for new rule", oldestRuleId);
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class MemoryStats {
        private long totalRules;
        private long activeRules;
        private long expiredRules;
        private int componentsTracked;
    }
}
