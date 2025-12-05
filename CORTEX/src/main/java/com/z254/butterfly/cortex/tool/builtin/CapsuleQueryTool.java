package com.z254.butterfly.cortex.tool.builtin;

import com.z254.butterfly.cortex.client.CapsuleClient;
import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.memory.capsule.CapsuleMemoryAdapter;
import com.z254.butterfly.cortex.tool.AgentTool;
import com.z254.butterfly.cortex.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Built-in tool for querying CAPSULE historical data.
 * Allows agents to retrieve past conversations and time-travel queries.
 */
@Component
@Slf4j
public class CapsuleQueryTool implements AgentTool {

    private static final String TOOL_ID = "cortex.capsule.query";
    private static final String TOOL_NAME = "capsule_query";
    
    private final CapsuleMemoryAdapter capsuleAdapter;

    private static final Map<String, Object> PARAMETER_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "query_type", Map.of(
                            "type", "string",
                            "enum", java.util.List.of("history", "time_travel", "by_rim_node"),
                            "description", "Type of historical query"
                    ),
                    "conversation_id", Map.of(
                            "type", "string",
                            "description", "Conversation ID for time-travel queries"
                    ),
                    "timestamp", Map.of(
                            "type", "string",
                            "description", "ISO timestamp for time-travel queries"
                    ),
                    "rim_node_id", Map.of(
                            "type", "string",
                            "description", "RIM node ID for related conversation lookup"
                    ),
                    "limit", Map.of(
                            "type", "integer",
                            "description", "Maximum number of results",
                            "default", 5
                    )
            ),
            "required", java.util.List.of("query_type")
    );

    public CapsuleQueryTool(CapsuleMemoryAdapter capsuleAdapter) {
        this.capsuleAdapter = capsuleAdapter;
    }

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
        return "Query CAPSULE for historical conversation data. " +
               "Use 'history' to get past conversations with this user. " +
               "Use 'time_travel' to see conversation state at a specific time. " +
               "Use 'by_rim_node' to find conversations related to a RIM entity.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return PARAMETER_SCHEMA;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> parameters, AgentContext context) {
        String queryType = (String) parameters.get("query_type");
        int limit = parameters.containsKey("limit") ? ((Number) parameters.get("limit")).intValue() : 5;
        
        log.debug("Executing CAPSULE query: type={}", queryType);

        return switch (queryType) {
            case "history" -> executeHistoryQuery(context, limit);
            case "time_travel" -> executeTimeTravelQuery(parameters, context);
            case "by_rim_node" -> executeRimNodeQuery(parameters, limit);
            default -> Mono.just(ToolResult.failure(TOOL_ID, "INVALID_QUERY_TYPE",
                    "Unknown query type: " + queryType));
        };
    }

    private Mono<ToolResult> executeHistoryQuery(AgentContext context, int limit) {
        return capsuleAdapter.getHistoricalContext(
                        context.getAgentId(),
                        context.getUserId(),
                        limit)
                .map(snapshot -> String.format(
                        "- %s: %s (messages: %d, tokens: %d)",
                        snapshot.snapshotAt(),
                        snapshot.summary() != null ? snapshot.summary() : "No summary",
                        snapshot.messageCount(),
                        snapshot.totalTokens()
                ))
                .collectList()
                .map(results -> {
                    if (results.isEmpty()) {
                        return ToolResult.success(TOOL_ID,
                                "No historical conversations found for this user.");
                    }
                    String output = "Historical conversations:\n" + String.join("\n", results);
                    return ToolResult.success(TOOL_ID, output);
                });
    }

    private Mono<ToolResult> executeTimeTravelQuery(Map<String, Object> params, AgentContext context) {
        String conversationId = (String) params.get("conversation_id");
        String timestampStr = (String) params.get("timestamp");

        if (conversationId == null && context.getConversationId() != null) {
            conversationId = context.getConversationId();
        }

        if (conversationId == null) {
            return Mono.just(ToolResult.failure(TOOL_ID, "MISSING_PARAM",
                    "conversation_id is required for time-travel queries"));
        }

        Instant timestamp;
        try {
            timestamp = timestampStr != null ? Instant.parse(timestampStr) : Instant.now();
        } catch (Exception e) {
            return Mono.just(ToolResult.failure(TOOL_ID, "INVALID_TIMESTAMP",
                    "Invalid timestamp format. Use ISO-8601 format."));
        }

        return capsuleAdapter.getConversationAtTime(conversationId, timestamp)
                .map(snapshot -> {
                    String output = String.format(
                            "Conversation state at %s:\n" +
                            "Status: %s\n" +
                            "Messages: %d\n" +
                            "Total tokens: %d\n" +
                            "Summary: %s",
                            timestamp,
                            snapshot.status(),
                            snapshot.messageCount(),
                            snapshot.totalTokens(),
                            snapshot.summary() != null ? snapshot.summary() : "No summary"
                    );
                    return ToolResult.success(TOOL_ID, output);
                })
                .defaultIfEmpty(ToolResult.success(TOOL_ID,
                        "No snapshot found for this conversation at the specified time."));
    }

    private Mono<ToolResult> executeRimNodeQuery(Map<String, Object> params, int limit) {
        String rimNodeId = (String) params.get("rim_node_id");

        if (rimNodeId == null) {
            return Mono.just(ToolResult.failure(TOOL_ID, "MISSING_PARAM",
                    "rim_node_id is required for RIM node queries"));
        }

        return capsuleAdapter.getContextByRimNode(rimNodeId, limit)
                .map(snapshot -> String.format(
                        "- %s: %s (user: %s)",
                        snapshot.snapshotAt(),
                        snapshot.summary() != null ? snapshot.summary() : "No summary",
                        snapshot.userId()
                ))
                .collectList()
                .map(results -> {
                    if (results.isEmpty()) {
                        return ToolResult.success(TOOL_ID,
                                "No conversations found related to RIM node: " + rimNodeId);
                    }
                    String output = "Related conversations:\n" + String.join("\n", results);
                    return ToolResult.success(TOOL_ID, output);
                });
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }
}
