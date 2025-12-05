package com.z254.butterfly.cortex.tool.builtin;

import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.tool.AgentTool;
import com.z254.butterfly.cortex.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Built-in tool for querying ODYSSEY world state and futures.
 * Allows agents to get strategic context and path projections.
 */
@Component
@Slf4j
public class OdysseyQueryTool implements AgentTool {

    private static final String TOOL_ID = "cortex.odyssey.query";
    private static final String TOOL_NAME = "odyssey_query";

    private static final Map<String, Object> PARAMETER_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "query_type", Map.of(
                            "type", "string",
                            "enum", java.util.List.of("world_state", "actor_model", "path_projection", "strategic_context"),
                            "description", "Type of ODYSSEY query"
                    ),
                    "actor_id", Map.of(
                            "type", "string",
                            "description", "Actor ID for actor model queries"
                    ),
                    "scenario_id", Map.of(
                            "type", "string",
                            "description", "Scenario ID for path projections"
                    ),
                    "context_key", Map.of(
                            "type", "string",
                            "description", "Context key for strategic context queries"
                    )
            ),
            "required", java.util.List.of("query_type")
    );

    @Override
    public String getId() {
        return TOOL_ID;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Query the ODYSSEY service for world state, actor models, and strategic context. " +
               "Use 'world_state' to get current global state. " +
               "Use 'actor_model' to understand an actor's motivations and history. " +
               "Use 'path_projection' to see possible futures. " +
               "Use 'strategic_context' to get relevant background for decisions.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return PARAMETER_SCHEMA;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> parameters, AgentContext context) {
        String queryType = (String) parameters.get("query_type");
        
        log.debug("Executing ODYSSEY query: type={}", queryType);

        // TODO: Replace with actual ODYSSEY client call
        return Mono.fromCallable(() -> {
            switch (queryType) {
                case "world_state":
                    return executeWorldStateQuery();
                case "actor_model":
                    return executeActorModelQuery(parameters);
                case "path_projection":
                    return executePathProjectionQuery(parameters);
                case "strategic_context":
                    return executeStrategicContextQuery(parameters);
                default:
                    return ToolResult.failure(TOOL_ID, "INVALID_QUERY_TYPE",
                            "Unknown query type: " + queryType);
            }
        });
    }

    private ToolResult executeWorldStateQuery() {
        String output = "World State Summary:\n" +
                "[Stub - ODYSSEY integration pending]\n" +
                "No world state data available in stub mode.\n" +
                "When integrated, this will provide current global context.";
        return ToolResult.success(TOOL_ID, output);
    }

    private ToolResult executeActorModelQuery(Map<String, Object> params) {
        String actorId = (String) params.get("actor_id");
        String output = String.format(
                "Actor Model for %s:\n" +
                "[Stub - ODYSSEY integration pending]\n" +
                "No actor model data available in stub mode.\n" +
                "When integrated, this will provide actor motivations, capabilities, and history.",
                actorId != null ? actorId : "unspecified"
        );
        return ToolResult.success(TOOL_ID, output);
    }

    private ToolResult executePathProjectionQuery(Map<String, Object> params) {
        String scenarioId = (String) params.get("scenario_id");
        String output = String.format(
                "Path Projection for scenario %s:\n" +
                "[Stub - ODYSSEY integration pending]\n" +
                "No path projection data available in stub mode.\n" +
                "When integrated, this will provide possible future states and probabilities.",
                scenarioId != null ? scenarioId : "default"
        );
        return ToolResult.success(TOOL_ID, output);
    }

    private ToolResult executeStrategicContextQuery(Map<String, Object> params) {
        String contextKey = (String) params.get("context_key");
        String output = String.format(
                "Strategic Context for %s:\n" +
                "[Stub - ODYSSEY integration pending]\n" +
                "No strategic context available in stub mode.\n" +
                "When integrated, this will provide relevant background for decisions.",
                contextKey != null ? contextKey : "general"
        );
        return ToolResult.success(TOOL_ID, output);
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }
}
