package com.z254.butterfly.cortex.llm;

import com.z254.butterfly.cortex.config.CortexProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry for LLM providers.
 * Manages provider registration, routing, and fallback.
 */
@Component
@Slf4j
public class LLMProviderRegistry {

    private final Map<String, LLMProvider> providers = new HashMap<>();
    private final CortexProperties cortexProperties;
    private final String defaultProviderId;

    public LLMProviderRegistry(List<LLMProvider> providerList, CortexProperties cortexProperties) {
        this.cortexProperties = cortexProperties;
        this.defaultProviderId = cortexProperties.getLlm().getDefaultProvider();
        
        for (LLMProvider provider : providerList) {
            providers.put(provider.getProviderId(), provider);
            log.info("Registered LLM provider: {}", provider.getProviderId());
        }
        
        log.info("Default LLM provider: {}", defaultProviderId);
    }

    /**
     * Get a provider by ID.
     *
     * @param providerId the provider ID
     * @return the provider
     * @throws IllegalArgumentException if provider not found
     */
    public LLMProvider getProvider(String providerId) {
        LLMProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("No LLM provider found: " + providerId);
        }
        return provider;
    }

    /**
     * Get the default provider.
     *
     * @return the default provider
     */
    public LLMProvider getDefaultProvider() {
        return getProvider(defaultProviderId);
    }

    /**
     * Get a provider, falling back to default if not found.
     *
     * @param providerId the provider ID (or null for default)
     * @return the provider
     */
    public LLMProvider getProviderOrDefault(String providerId) {
        if (providerId == null || providerId.isEmpty()) {
            return getDefaultProvider();
        }
        LLMProvider provider = providers.get(providerId);
        return provider != null ? provider : getDefaultProvider();
    }

    /**
     * Get the best available provider with failover.
     * Tries the requested provider, then falls back to alternatives.
     *
     * @param preferredProviderId preferred provider ID
     * @return mono with the best available provider
     */
    public Mono<LLMProvider> getBestAvailableProvider(String preferredProviderId) {
        String[] providerOrder = determineProviderOrder(preferredProviderId);
        
        return checkProviderAvailability(providerOrder, 0);
    }

    private Mono<LLMProvider> checkProviderAvailability(String[] providerOrder, int index) {
        if (index >= providerOrder.length) {
            return Mono.error(new IllegalStateException("No LLM providers available"));
        }

        String providerId = providerOrder[index];
        LLMProvider provider = providers.get(providerId);
        
        if (provider == null) {
            return checkProviderAvailability(providerOrder, index + 1);
        }

        return provider.isAvailable()
                .flatMap(available -> {
                    if (available) {
                        log.debug("Using LLM provider: {}", providerId);
                        return Mono.just(provider);
                    } else {
                        log.warn("LLM provider {} not available, trying next", providerId);
                        return checkProviderAvailability(providerOrder, index + 1);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Error checking LLM provider {}: {}", providerId, e.getMessage());
                    return checkProviderAvailability(providerOrder, index + 1);
                });
    }

    private String[] determineProviderOrder(String preferredProviderId) {
        // Build priority order: preferred -> default -> others
        java.util.List<String> order = new java.util.ArrayList<>();
        
        if (preferredProviderId != null && providers.containsKey(preferredProviderId)) {
            order.add(preferredProviderId);
        }
        
        if (!order.contains(defaultProviderId) && providers.containsKey(defaultProviderId)) {
            order.add(defaultProviderId);
        }
        
        // Add remaining providers
        for (String id : providers.keySet()) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        
        return order.toArray(new String[0]);
    }

    /**
     * Check if a provider exists.
     *
     * @param providerId the provider ID
     * @return true if the provider exists
     */
    public boolean hasProvider(String providerId) {
        return providers.containsKey(providerId);
    }

    /**
     * Get all registered provider IDs.
     *
     * @return set of provider IDs
     */
    public Set<String> getProviderIds() {
        return providers.keySet();
    }

    /**
     * Get the default provider ID.
     *
     * @return default provider ID
     */
    public String getDefaultProviderId() {
        return defaultProviderId;
    }
}
