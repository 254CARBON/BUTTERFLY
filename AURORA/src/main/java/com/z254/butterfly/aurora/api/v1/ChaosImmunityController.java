package com.z254.butterfly.aurora.api.v1;

import com.z254.butterfly.aurora.domain.model.ImmunityRule;
import com.z254.butterfly.aurora.immunity.ChaosImmunizer;
import com.z254.butterfly.aurora.immunity.ChaosMemory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for chaos immunity operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/immunity")
@Tag(name = "Immunity", description = "Chaos immunity rules and learning")
public class ChaosImmunityController {

    private final ChaosImmunizer chaosImmunizer;
    private final ChaosMemory chaosMemory;

    public ChaosImmunityController(ChaosImmunizer chaosImmunizer, 
                                    ChaosMemory chaosMemory) {
        this.chaosImmunizer = chaosImmunizer;
        this.chaosMemory = chaosMemory;
    }

    @GetMapping("/rules")
    @Operation(summary = "List immunity rules", description = "List all immunity rules")
    public Mono<ResponseEntity<RulesResponse>> listRules(
            @Parameter(description = "Filter by component") 
            @RequestParam(required = false) String component,
            @Parameter(description = "Filter by pattern type") 
            @RequestParam(required = false) String patternType,
            @Parameter(description = "Show only active rules") 
            @RequestParam(defaultValue = "true") boolean activeOnly) {

        if (component != null) {
            return chaosMemory.findRulesForComponent(component)
                    .filter(rule -> !activeOnly || rule.isActive())
                    .filter(rule -> patternType == null || 
                            rule.getPatternType().equals(patternType))
                    .collectList()
                    .map(rules -> ResponseEntity.ok(RulesResponse.builder()
                            .rules(rules)
                            .total(rules.size())
                            .build()));
        }

        return chaosMemory.getAllRules()
                .filter(rule -> !activeOnly || rule.isActive())
                .filter(rule -> patternType == null || 
                        rule.getPatternType().equals(patternType))
                .collectList()
                .map(rules -> ResponseEntity.ok(RulesResponse.builder()
                        .rules(rules)
                        .total(rules.size())
                        .build()));
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "Get immunity rule", description = "Get immunity rule by ID")
    public Mono<ResponseEntity<ImmunityRule>> getRule(
            @Parameter(description = "Rule ID") @PathVariable String id) {

        return chaosMemory.findRule(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/rules")
    @Operation(summary = "Create immunity rule", description = "Create a manual immunity rule")
    public Mono<ResponseEntity<ImmunityRule>> createRule(@RequestBody CreateRuleRequest request) {

        log.info("Creating manual immunity rule for component: {}", request.getComponent());

        ImmunityRule rule = ImmunityRule.builder()
                .ruleId("RULE-MANUAL-" + System.currentTimeMillis())
                .component(request.getComponent())
                .patternType(request.getPatternType())
                .anomalyType(request.getAnomalyType())
                .triggerCondition(request.getTriggerCondition())
                .recommendedAction(request.getRecommendedAction())
                .actionParameters(request.getParameters())
                .confidence(request.getConfidence() != null ? request.getConfidence() : 1.0)
                .validFrom(Instant.now())
                .validUntil(request.getValidUntil())
                .active(true)
                .priority(request.getPriority() != null ? request.getPriority() : 3)
                .cooldownMs(request.getCooldownMs() != null ? request.getCooldownMs() : 60000)
                .createdAt(Instant.now())
                .build();

        return chaosMemory.storeRule(rule)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "Delete immunity rule", description = "Delete an immunity rule")
    public Mono<ResponseEntity<Void>> deleteRule(
            @Parameter(description = "Rule ID") @PathVariable String id) {

        return chaosMemory.deleteRule(id)
                .thenReturn(ResponseEntity.ok().<Void>build());
    }

    @PostMapping("/rules/{id}/deactivate")
    @Operation(summary = "Deactivate rule", description = "Deactivate an immunity rule")
    public Mono<ResponseEntity<Void>> deactivateRule(
            @Parameter(description = "Rule ID") @PathVariable String id) {

        return chaosMemory.deactivateRule(id)
                .thenReturn(ResponseEntity.ok().<Void>build());
    }

    @GetMapping("/summary/{component}")
    @Operation(summary = "Get immunity summary", 
               description = "Get immunity summary for a component")
    public Mono<ResponseEntity<ChaosImmunizer.ImmunitySummary>> getImmunitySummary(
            @Parameter(description = "Component ID") @PathVariable String component) {

        return chaosImmunizer.getImmunitySummary(component)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get immunity stats", description = "Get overall immunity statistics")
    public Mono<ResponseEntity<ChaosMemory.MemoryStats>> getStats() {
        return Mono.just(ResponseEntity.ok(chaosMemory.getStats()));
    }

    @PostMapping("/cleanup")
    @Operation(summary = "Cleanup expired rules", description = "Remove expired immunity rules")
    public Mono<ResponseEntity<CleanupResponse>> cleanupExpiredRules() {
        return chaosMemory.cleanupExpiredRules()
                .map(count -> ResponseEntity.ok(CleanupResponse.builder()
                        .removedCount(count)
                        .timestamp(Instant.now())
                        .build()));
    }

    // ========== Request/Response DTOs ==========

    @lombok.Data
    public static class CreateRuleRequest {
        private String component;
        private String patternType;
        private String anomalyType;
        private String triggerCondition;
        private String recommendedAction;
        private Map<String, String> parameters;
        private Double confidence;
        private Instant validUntil;
        private Integer priority;
        private Long cooldownMs;
    }

    @lombok.Data
    @lombok.Builder
    public static class RulesResponse {
        private List<ImmunityRule> rules;
        private int total;
    }

    @lombok.Data
    @lombok.Builder
    public static class CleanupResponse {
        private int removedCount;
        private Instant timestamp;
    }
}
