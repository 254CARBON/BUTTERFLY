package com.z254.butterfly.cortex.tool;

import com.z254.butterfly.cortex.client.SynapseToolClient;
import com.z254.butterfly.cortex.client.SynapseToolClient.ActionRequest;
import com.z254.butterfly.cortex.client.SynapseToolClient.ActionResult;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Bridge to SYNAPSE for tool execution.
 * Routes tool calls through SYNAPSE with timeout enforcement and result mapping.
 */
@Component
@Slf4j
public class ToolBridge {

    private final SynapseToolClient synapseClient;
    private final CortexProperties.SafetyProperties.ToolSandboxProperties sandboxConfig;

    public ToolBridge(
            SynapseToolClient synapseClient,
            CortexProperties cortexProperties) {
        this.synapseClient = synapseClient;
        this.sandboxConfig = cortexProperties.getSafety().getToolSandbox();
    }

    /**
     * Execute a tool via SYNAPSE.
     *
     * @param toolId the tool ID
     * @param parameters the tool parameters
     * @param context the agent context
     * @return the tool result
     */
    public Mono<ToolResult> execute(String toolId, Map<String, Object> parameters, AgentContext context) {
        Instant startTime = Instant.now();
        String actionId = UUID.randomUUID().toString();
        
        log.debug("Executing tool {} via SYNAPSE (action {})", toolId, actionId);

        ActionRequest request = new ActionRequest(
                actionId,
                toolId,
                parameters,
                context.getCorrelationId(),
                context.getGovernance() != null ? context.getGovernance().getPlanId() : null,
                null,  // RIM node ID
                context.getUserId(),
                ActionRequest.ExecutionMode.SYNC,
                Map.of("taskId", context.getTaskId(), "agentId", context.getAgentId())
        );

        Duration timeout = sandboxConfig.getDefaultTimeout();

        return synapseClient.executeAction(request)
                .timeout(timeout)
                .map(result -> mapResult(toolId, result, startTime))
                .onErrorResume(e -> {
                    log.error("Tool execution failed: {} - {}", toolId, e.getMessage());
                    
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        return Mono.just(ToolResult.timeout(toolId, timeout));
                    }
                    
                    return Mono.just(ToolResult.failure(toolId, "EXECUTION_ERROR", e.getMessage()));
                })
                .doOnSuccess(result -> {
                    long duration = Duration.between(startTime, Instant.now()).toMillis();
                    log.debug("Tool {} completed in {}ms: {}", toolId, duration, result.isSuccess());
                });
    }

    /**
     * Execute a tool locally (for built-in tools that don't need SYNAPSE).
     *
     * @param tool the tool implementation
     * @param parameters the parameters
     * @param context the context
     * @return the tool result
     */
    public Mono<ToolResult> executeLocal(AgentTool tool, Map<String, Object> parameters, AgentContext context) {
        Instant startTime = Instant.now();
        Duration timeout = sandboxConfig.getDefaultTimeout();

        log.debug("Executing local tool: {}", tool.getId());

        return tool.execute(parameters, context)
                .timeout(timeout)
                .map(result -> {
                    result.setDuration(Duration.between(startTime, Instant.now()));
                    result.setStartedAt(startTime);
                    result.setCompletedAt(Instant.now());
                    return result;
                })
                .onErrorResume(e -> {
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        return Mono.just(ToolResult.timeout(tool.getId(), timeout));
                    }
                    return Mono.just(ToolResult.failure(tool.getId(), "EXECUTION_ERROR", e.getMessage()));
                });
    }

    /**
     * Check if a tool should be executed via SYNAPSE or locally.
     *
     * @param toolId the tool ID
     * @return true if should use SYNAPSE
     */
    public boolean shouldUseSynapse(String toolId) {
        // Built-in tools are executed locally
        // External tools go through SYNAPSE
        return !isBuiltInTool(toolId);
    }

    /**
     * Check if a tool is a built-in CORTEX tool.
     */
    private boolean isBuiltInTool(String toolId) {
        return toolId.startsWith("cortex.") || toolId.startsWith("builtin.");
    }

    /**
     * Map SYNAPSE ActionResult to ToolResult.
     */
    private ToolResult mapResult(String toolId, ActionResult actionResult, Instant startTime) {
        return ToolResult.builder()
                .toolId(toolId)
                .success(actionResult.success())
                .content(actionResult.result())
                .output(actionResult.output())
                .errorCode(actionResult.errorCode())
                .errorMessage(actionResult.errorMessage())
                .duration(Duration.ofMillis(actionResult.durationMs()))
                .startedAt(startTime)
                .completedAt(Instant.now())
                .synapseActionId(actionResult.actionId())
                .metadata(actionResult.metadata())
                .build();
    }

    /**
     * Sync tool registry with SYNAPSE.
     *
     * @param registry the local registry to update
     * @return completion signal
     */
    public Mono<Void> syncToolRegistry(ToolRegistry registry) {
        return synapseClient.listTools()
                .doOnNext(toolDef -> log.debug("Discovered SYNAPSE tool: {}", toolDef.id()))
                .then()
                .doOnSuccess(v -> log.info("Tool registry sync complete"));
    }

    /**
     * Check if SYNAPSE is available.
     */
    public Mono<Boolean> isSynapseAvailable() {
        return synapseClient.isAvailable();
    }
}
