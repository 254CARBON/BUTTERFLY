package com.z254.butterfly.cortex.client.impl;

import com.z254.butterfly.cortex.client.SynapseToolClient;
import com.z254.butterfly.cortex.config.CortexProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * WebClient-based implementation of SynapseToolClient.
 */
@Component
@Slf4j
public class WebClientSynapseToolClient implements SynapseToolClient {

    private final WebClient webClient;
    private final CortexProperties.ClientProperties.SynapseClientProperties config;
    private final boolean stubMode;

    public WebClientSynapseToolClient(CortexProperties cortexProperties) {
        this.config = cortexProperties.getClients().getSynapse();
        
        this.stubMode = config.getBaseUrl() == null || config.getBaseUrl().isEmpty();
        
        if (!stubMode) {
            this.webClient = WebClient.builder()
                    .baseUrl(config.getBaseUrl())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        } else {
            this.webClient = null;
            log.warn("SYNAPSE client running in stub mode");
        }
    }

    @Override
    @CircuitBreaker(name = "synapse")
    @Retry(name = "llm")
    public Mono<ActionResult> executeAction(ActionRequest request) {
        if (stubMode) {
            return Mono.just(new ActionResult(
                    request.actionId(),
                    request.toolId(),
                    true,
                    Map.of("stub", true),
                    "Stub result for " + request.toolId(),
                    null,
                    null,
                    100,
                    Map.of()
            ));
        }

        return webClient.post()
                .uri("/api/v1/actions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ActionResult.class)
                .timeout(config.getTimeout())
                .doOnSuccess(result -> log.debug("Action {} completed: {}", 
                        request.actionId(), result.success()))
                .doOnError(e -> log.error("Action {} failed: {}", 
                        request.actionId(), e.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "synapse")
    public Mono<ActionStatus> getActionStatus(String actionId) {
        if (stubMode) {
            return Mono.just(new ActionStatus(
                    actionId, "stub", ActionStatus.Status.COMPLETED, null, 100, "Complete"
            ));
        }

        return webClient.get()
                .uri("/api/v1/actions/{id}/status", actionId)
                .retrieve()
                .bodyToMono(ActionStatus.class)
                .timeout(config.getTimeout());
    }

    @Override
    @CircuitBreaker(name = "synapse")
    public Mono<Boolean> cancelAction(String actionId, String reason) {
        if (stubMode) {
            return Mono.just(true);
        }

        return webClient.post()
                .uri("/api/v1/actions/{id}/cancel", actionId)
                .bodyValue(Map.of("reason", reason))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(config.getTimeout())
                .map(response -> Boolean.TRUE.equals(response.get("cancelled")));
    }

    @Override
    @CircuitBreaker(name = "synapse")
    public Flux<ToolDefinition> listTools() {
        if (stubMode) {
            return Flux.empty();
        }

        return webClient.get()
                .uri("/api/v1/tools")
                .retrieve()
                .bodyToFlux(ToolDefinition.class)
                .timeout(config.getTimeout());
    }

    @Override
    @CircuitBreaker(name = "synapse")
    public Mono<ToolDefinition> getTool(String toolId) {
        if (stubMode) {
            return Mono.empty();
        }

        return webClient.get()
                .uri("/api/v1/tools/{id}", toolId)
                .retrieve()
                .bodyToMono(ToolDefinition.class)
                .timeout(config.getTimeout());
    }

    @Override
    public Mono<Boolean> isAvailable() {
        if (stubMode) {
            return Mono.just(false);
        }

        return webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .map(response -> "UP".equals(response.get("status")))
                .onErrorReturn(false);
    }
}
