package com.z254.butterfly.cortex.tool;

import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.llm.LLMRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for agent tools.
 * Manages tool registration, lookup, and conversion to LLM format.
 */
@Component
@Slf4j
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<AgentTool> toolList) {
        for (AgentTool tool : toolList) {
            tools.put(tool.getId(), tool);
            log.info("Registered tool: {} ({})", tool.getName(), tool.getId());
        }
    }

    /**
     * Register a tool.
     *
     * @param tool the tool to register
     */
    public void register(AgentTool tool) {
        tools.put(tool.getId(), tool);
        log.info("Registered tool: {} ({})", tool.getName(), tool.getId());
    }

    /**
     * Unregister a tool.
     *
     * @param toolId the tool ID
     */
    public void unregister(String toolId) {
        AgentTool removed = tools.remove(toolId);
        if (removed != null) {
            log.info("Unregistered tool: {}", toolId);
        }
    }

    /**
     * Get a tool by ID.
     *
     * @param toolId the tool ID
     * @return the tool or empty
     */
    public Optional<AgentTool> getTool(String toolId) {
        return Optional.ofNullable(tools.get(toolId));
    }

    /**
     * Get a tool by ID, throwing if not found.
     *
     * @param toolId the tool ID
     * @return the tool
     * @throws IllegalArgumentException if tool not found
     */
    public AgentTool getToolOrThrow(String toolId) {
        AgentTool tool = tools.get(toolId);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not found: " + toolId);
        }
        return tool;
    }

    /**
     * Get all tools.
     *
     * @return all tools
     */
    public Collection<AgentTool> getAllTools() {
        return tools.values();
    }

    /**
     * Get tools by IDs.
     *
     * @param toolIds the tool IDs
     * @return matching tools
     */
    public List<AgentTool> getTools(Set<String> toolIds) {
        List<AgentTool> result = new ArrayList<>();
        for (String id : toolIds) {
            AgentTool tool = tools.get(id);
            if (tool != null) {
                result.add(tool);
            }
        }
        return result;
    }

    /**
     * Get tools available in a context.
     *
     * @param context the execution context
     * @return available tools
     */
    public Flux<AgentTool> getAvailableTools(AgentContext context) {
        Set<String> allowedIds = context.getAvailableTools();
        return Flux.fromIterable(tools.values())
                .filter(tool -> allowedIds == null || allowedIds.isEmpty() || allowedIds.contains(tool.getId()))
                .filter(tool -> tool.isAvailable(context));
    }

    /**
     * Convert tools to LLM format.
     *
     * @param toolIds the tool IDs to convert
     * @return LLM tool definitions
     */
    public List<LLMRequest.Tool> toLLMTools(Set<String> toolIds) {
        List<LLMRequest.Tool> llmTools = new ArrayList<>();
        for (String id : toolIds) {
            AgentTool tool = tools.get(id);
            if (tool != null) {
                llmTools.add(toLLMTool(tool));
            }
        }
        return llmTools;
    }

    /**
     * Convert a tool to LLM format.
     *
     * @param tool the tool
     * @return LLM tool definition
     */
    public LLMRequest.Tool toLLMTool(AgentTool tool) {
        return LLMRequest.Tool.builder()
                .type("function")
                .function(LLMRequest.Function.builder()
                        .name(tool.getName())
                        .description(tool.getDescription())
                        .parameters(tool.getParameterSchema())
                        .build())
                .build();
    }

    /**
     * Execute a tool.
     *
     * @param toolId the tool ID
     * @param parameters the parameters
     * @param context the execution context
     * @return the result
     */
    public Mono<ToolResult> execute(String toolId, Map<String, Object> parameters, AgentContext context) {
        AgentTool tool = tools.get(toolId);
        if (tool == null) {
            return Mono.just(ToolResult.failure(toolId, "TOOL_NOT_FOUND", "Tool not found: " + toolId));
        }

        return tool.validate(parameters)
                .flatMap(validation -> {
                    if (!validation.valid()) {
                        return Mono.just(ToolResult.failure(toolId, "VALIDATION_ERROR", validation.message()));
                    }
                    return tool.execute(parameters, context);
                })
                .onErrorResume(e -> {
                    log.error("Tool execution failed: {} - {}", toolId, e.getMessage());
                    return Mono.just(ToolResult.failure(toolId, "EXECUTION_ERROR", e.getMessage()));
                });
    }

    /**
     * Check if a tool exists.
     *
     * @param toolId the tool ID
     * @return true if exists
     */
    public boolean hasTool(String toolId) {
        return tools.containsKey(toolId);
    }

    /**
     * Get tool count.
     *
     * @return number of registered tools
     */
    public int size() {
        return tools.size();
    }
}
