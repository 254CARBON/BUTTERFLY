package com.z254.butterfly.cortex.llm.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.llm.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Anthropic LLM Provider implementation.
 * Supports Claude 3 Opus, Sonnet, and Haiku with tool use and streaming.
 */
@Component
@ConditionalOnProperty(prefix = "cortex.llm.anthropic", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class AnthropicProvider implements LLMProvider {

    private static final String PROVIDER_ID = "anthropic";
    private static final String MESSAGES_PATH = "/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final CortexProperties.LLMProperties.AnthropicProperties config;
    private final Timer llmCallTimer;
    private final Counter llmCallCounter;
    private final Counter llmErrorCounter;

    public AnthropicProvider(
            CortexProperties cortexProperties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.config = cortexProperties.getLlm().getAnthropic();
        this.objectMapper = objectMapper;
        
        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader("x-api-key", config.getApiKey())
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        
        this.llmCallTimer = Timer.builder("cortex.llm.call.latency")
                .tag("provider", PROVIDER_ID)
                .register(meterRegistry);
        this.llmCallCounter = Counter.builder("cortex.llm.calls")
                .tag("provider", PROVIDER_ID)
                .register(meterRegistry);
        this.llmErrorCounter = Counter.builder("cortex.llm.errors")
                .tag("provider", PROVIDER_ID)
                .register(meterRegistry);
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supportsTools() {
        return true;
    }

    @Override
    public String getDefaultModel() {
        return config.getModel();
    }

    @Override
    @CircuitBreaker(name = "anthropic")
    @Retry(name = "llm")
    public Mono<LLMResponse> complete(LLMRequest request) {
        llmCallCounter.increment();
        long startTime = System.currentTimeMillis();
        
        Map<String, Object> body = buildRequestBody(request, false);
        
        return webClient.post()
                .uri(MESSAGES_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(config.getTimeout())
                .map(json -> parseResponse(json, startTime))
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    llmCallTimer.record(Duration.ofMillis(duration));
                    log.debug("Anthropic completion: {}ms, {} tokens", duration, 
                            response.getTotalTokens());
                })
                .doOnError(e -> {
                    llmErrorCounter.increment();
                    log.error("Anthropic completion error: {}", e.getMessage());
                });
    }

    @Override
    @CircuitBreaker(name = "anthropic")
    public Flux<LLMChunk> stream(LLMRequest request) {
        llmCallCounter.increment();
        
        Map<String, Object> body = buildRequestBody(request, true);
        
        return webClient.post()
                .uri(MESSAGES_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isEmpty())
                .map(this::parseStreamEvent)
                .filter(Objects::nonNull)
                .doOnError(e -> {
                    llmErrorCounter.increment();
                    log.error("Anthropic streaming error: {}", e.getMessage());
                });
    }

    @Override
    public Mono<List<Float>> embed(String text) {
        // Anthropic doesn't have a native embedding API
        // Return error or use fallback
        return Mono.error(new UnsupportedOperationException(
                "Anthropic does not support embeddings. Use OpenAI or Ollama for embeddings."));
    }

    @Override
    public Mono<List<List<Float>>> embedBatch(List<String> texts) {
        return Mono.error(new UnsupportedOperationException(
                "Anthropic does not support embeddings. Use OpenAI or Ollama for embeddings."));
    }

    @Override
    public Mono<Boolean> isAvailable() {
        // Simple health check - try a minimal request
        return Mono.just(config.getApiKey() != null && !config.getApiKey().isEmpty());
    }

    private Map<String, Object> buildRequestBody(LLMRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        
        body.put("model", request.getModel() != null ? request.getModel() : config.getModel());
        body.put("stream", stream);
        
        // Max tokens is required for Anthropic
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : config.getMaxTokens());
        
        // Extract system message
        String systemMessage = null;
        List<LLMRequest.Message> userMessages = new ArrayList<>();
        
        for (LLMRequest.Message msg : request.getMessages()) {
            if ("system".equals(msg.getRole())) {
                systemMessage = msg.getContent();
            } else {
                userMessages.add(msg);
            }
        }
        
        if (systemMessage != null) {
            body.put("system", systemMessage);
        }
        
        // Build messages (excluding system)
        List<Map<String, Object>> messages = userMessages.stream()
                .map(this::convertMessage)
                .collect(Collectors.toList());
        body.put("messages", messages);
        
        // Temperature
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        
        // Stop sequences
        if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
            body.put("stop_sequences", request.getStopSequences());
        }
        
        // Tools
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = request.getTools().stream()
                    .map(this::convertTool)
                    .collect(Collectors.toList());
            body.put("tools", tools);
        }
        
        return body;
    }

    private Map<String, Object> convertMessage(LLMRequest.Message message) {
        Map<String, Object> msg = new HashMap<>();
        
        String role = "user".equals(message.getRole()) || "assistant".equals(message.getRole()) 
                ? message.getRole() 
                : "user";
        msg.put("role", role);
        
        // Anthropic uses content blocks
        List<Map<String, Object>> contentBlocks = new ArrayList<>();
        
        if (message.getContent() != null) {
            contentBlocks.add(Map.of("type", "text", "text", message.getContent()));
        }
        
        // Handle tool results
        if ("tool".equals(message.getRole()) && message.getToolCallId() != null) {
            msg.put("role", "user");
            contentBlocks.add(Map.of(
                    "type", "tool_result",
                    "tool_use_id", message.getToolCallId(),
                    "content", message.getContent()
            ));
        }
        
        // Handle tool use by assistant
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            for (LLMRequest.ToolCall tc : message.getToolCalls()) {
                try {
                    Map<String, Object> input = objectMapper.readValue(
                            tc.getFunction().getArguments(), Map.class);
                    contentBlocks.add(Map.of(
                            "type", "tool_use",
                            "id", tc.getId(),
                            "name", tc.getFunction().getName(),
                            "input", input
                    ));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse tool arguments: {}", e.getMessage());
                }
            }
        }
        
        msg.put("content", contentBlocks);
        return msg;
    }

    private Map<String, Object> convertTool(LLMRequest.Tool tool) {
        return Map.of(
                "name", tool.getFunction().getName(),
                "description", tool.getFunction().getDescription(),
                "input_schema", tool.getFunction().getParameters()
        );
    }

    private LLMResponse parseResponse(JsonNode json, long startTime) {
        String content = null;
        List<LLMRequest.ToolCall> toolCalls = null;
        
        JsonNode contentArray = json.get("content");
        if (contentArray != null && contentArray.isArray()) {
            StringBuilder textContent = new StringBuilder();
            toolCalls = new ArrayList<>();
            
            for (JsonNode block : contentArray) {
                String type = block.get("type").asText();
                if ("text".equals(type)) {
                    textContent.append(block.get("text").asText());
                } else if ("tool_use".equals(type)) {
                    try {
                        toolCalls.add(LLMRequest.ToolCall.builder()
                                .id(block.get("id").asText())
                                .type("function")
                                .function(LLMRequest.FunctionCall.builder()
                                        .name(block.get("name").asText())
                                        .arguments(objectMapper.writeValueAsString(block.get("input")))
                                        .build())
                                .build());
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to serialize tool input: {}", e.getMessage());
                    }
                }
            }
            
            content = textContent.length() > 0 ? textContent.toString() : null;
            if (toolCalls.isEmpty()) toolCalls = null;
        }
        
        LLMResponse.FinishReason finishReason = parseStopReason(json.get("stop_reason").asText());
        
        LLMResponse.Usage usage = null;
        if (json.has("usage")) {
            JsonNode usageNode = json.get("usage");
            usage = LLMResponse.Usage.builder()
                    .promptTokens(usageNode.get("input_tokens").asInt())
                    .completionTokens(usageNode.get("output_tokens").asInt())
                    .totalTokens(usageNode.get("input_tokens").asInt() + usageNode.get("output_tokens").asInt())
                    .build();
        }
        
        return LLMResponse.builder()
                .id(json.get("id").asText())
                .model(json.get("model").asText())
                .providerId(PROVIDER_ID)
                .content(content)
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .usage(usage)
                .latencyMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private LLMChunk parseStreamEvent(String line) {
        try {
            // Anthropic SSE format: event: ...\ndata: {...}
            if (line.startsWith("event:")) {
                return null; // Skip event type lines
            }
            if (line.startsWith("data: ")) {
                line = line.substring(6);
            }
            
            JsonNode json = objectMapper.readTree(line);
            String type = json.get("type").asText();
            
            return switch (type) {
                case "content_block_delta" -> {
                    JsonNode delta = json.get("delta");
                    if (delta.has("text")) {
                        yield LLMChunk.content(null, null, delta.get("text").asText());
                    }
                    yield null;
                }
                case "message_stop" -> LLMChunk.finished(null, null, 
                        LLMResponse.FinishReason.STOP, null);
                case "message_delta" -> {
                    if (json.has("delta") && json.get("delta").has("stop_reason")) {
                        yield LLMChunk.finished(null, null,
                                parseStopReason(json.get("delta").get("stop_reason").asText()),
                                null);
                    }
                    yield null;
                }
                default -> null;
            };
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Anthropic stream event: {}", e.getMessage());
            return null;
        }
    }

    private LLMResponse.FinishReason parseStopReason(String reason) {
        if (reason == null) return LLMResponse.FinishReason.STOP;
        return switch (reason) {
            case "end_turn", "stop_sequence" -> LLMResponse.FinishReason.STOP;
            case "max_tokens" -> LLMResponse.FinishReason.LENGTH;
            case "tool_use" -> LLMResponse.FinishReason.TOOL_CALLS;
            default -> LLMResponse.FinishReason.STOP;
        };
    }

    @Override
    public int countTokens(String text) {
        // Approximation for Claude models
        return text != null ? (int) Math.ceil(text.length() / 4.0) : 0;
    }
}
