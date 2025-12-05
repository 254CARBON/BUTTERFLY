package com.z254.butterfly.cortex.tool.builtin;

import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.tool.AgentTool;
import com.z254.butterfly.cortex.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Built-in tool for querying PERCEPTION service.
 * Allows agents to look up entities, events, and RIM data.
 */
@Component
@Slf4j
public class PerceptionQueryTool implements AgentTool {

    private static final String TOOL_ID = "cortex.perception.query";
    private static final String TOOL_NAME = "perception_query";
    
    private static final Map<String, Object> PARAMETER_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "query_type", Map.of(
                            "type", "string",
                            "enum", java.util.List.of("entity", "event", "rim_node"),
                            "description", "Type of query to perform"
                    ),
                    "entity_id", Map.of(
                            "type", "string",
                            "description", "ID of the entity to look up"
                    ),
                    "entity_type", Map.of(
                            "type", "string",
                            "description", "Type of entity (person, organization, asset, etc.)"
                    ),
                    "event_type", Map.of(
                            "type", "string",
                            "description", "Type of event to search for"
                    ),
                    "rim_node_id", Map.of(
                            "type", "string",
                            "description", "RIM node ID to query"
                    ),
                    "limit", Map.of(
                            "type", "integer",
                            "description", "Maximum number of results",
                            "default", 10
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
        return "Query the PERCEPTION service to look up entities, events, or RIM nodes. " +
               "Use this to retrieve information about people, organizations, assets, " +
               "or historical events in the system.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return PARAMETER_SCHEMA;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> parameters, AgentContext context) {
        String queryType = (String) parameters.get("query_type");
        
        log.debug("Executing perception query: type={}", queryType);

        // TODO: Replace with actual PERCEPTION client call
        // For now, return stub response
        return Mono.fromCallable(() -> {
            switch (queryType) {
                case "entity":
                    return executeEntityQuery(parameters);
                case "event":
                    return executeEventQuery(parameters);
                case "rim_node":
                    return executeRimNodeQuery(parameters);
                default:
                    return ToolResult.failure(TOOL_ID, "INVALID_QUERY_TYPE", 
                            "Unknown query type: " + queryType);
            }
        });
    }

    private ToolResult executeEntityQuery(Map<String, Object> params) {
        String entityId = (String) params.get("entity_id");
        String entityType = (String) params.get("entity_type");
        
        // Stub response
        String output = String.format(
                "Entity lookup result:\n" +
                "ID: %s\n" +
                "Type: %s\n" +
                "Status: [Stub - PERCEPTION integration pending]\n" +
                "No additional entity data available in stub mode.",
                entityId != null ? entityId : "not specified",
                entityType != null ? entityType : "any"
        );
        
        return ToolResult.success(TOOL_ID, output);
    }

    private ToolResult executeEventQuery(Map<String, Object> params) {
        String eventType = (String) params.get("event_type");
        int limit = params.containsKey("limit") ? ((Number) params.get("limit")).intValue() : 10;
        
        // Stub response
        String output = String.format(
                "Event search result:\n" +
                "Event type: %s\n" +
                "Limit: %d\n" +
                "Results: [Stub - PERCEPTION integration pending]\n" +
                "No events found in stub mode.",
                eventType != null ? eventType : "any",
                limit
        );
        
        return ToolResult.success(TOOL_ID, output);
    }

    private ToolResult executeRimNodeQuery(Map<String, Object> params) {
        String rimNodeId = (String) params.get("rim_node_id");
        
        // Stub response
        String output = String.format(
                "RIM Node lookup result:\n" +
                "Node ID: %s\n" +
                "Status: [Stub - PERCEPTION integration pending]\n" +
                "No RIM data available in stub mode.",
                rimNodeId != null ? rimNodeId : "not specified"
        );
        
        return ToolResult.success(TOOL_ID, output);
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }
}
