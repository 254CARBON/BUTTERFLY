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
 * OpenAI LLM Provider implementation.
 * Supports GPT-4, GPT-4-turbo, GPT-3.5-turbo with function calling and streaming.
 */
@Component
@ConditionalOnProperty(prefix = "cortex.llm.openai", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OpenAIProvider implements LLMProvider {

    private static final String PROVIDER_ID = "openai";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final CortexProperties.LLMProperties.OpenAIProperties config;
    private final Timer llmCallTimer;
    private final Counter llmCallCounter;
    private final Counter llmErrorCounter;

    public OpenAIProvider(
            CortexProperties cortexProperties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.config = cortexProperties.getLlm().getOpenai();
        this.objectMapper = objectMapper;
        
        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
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
    @CircuitBreaker(name = "openai")
    @Retry(name = "llm")
    public Mono<LLMResponse> complete(LLMRequest request) {
        llmCallCounter.increment();
        long startTime = System.currentTimeMillis();
        
        Map<String, Object> body = buildRequestBody(request, false);
        
        return webClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(config.getTimeout())
                .map(json -> parseResponse(json, startTime))
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    llmCallTimer.record(Duration.ofMillis(duration));
                    log.debug("OpenAI completion: {}ms, {} tokens", duration, 
                            response.getTotalTokens());
                })
                .doOnError(e -> {
                    llmErrorCounter.increment();
                    log.error("OpenAI completion error: {}", e.getMessage());
                });
    }

    @Override
    @CircuitBreaker(name = "openai")
    public Flux<LLMChunk> stream(LLMRequest request) {
        llmCallCounter.increment();
        
        Map<String, Object> body = buildRequestBody(request, true);
        
        return webClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isEmpty() && !line.equals("[DONE]"))
                .map(line -> {
                    // SSE format: data: {...}
                    if (line.startsWith("data: ")) {
                        line = line.substring(6);
                    }
                    if (line.equals("[DONE]")) {
                        return LLMChunk.finished(null, request.getModel(), 
                                LLMResponse.FinishReason.STOP, null);
                    }
                    return parseStreamChunk(line);
                })
                .filter(Objects::nonNull)
                .doOnError(e -> {
                    llmErrorCounter.increment();
                    log.error("OpenAI streaming error: {}", e.getMessage());
                });
    }

    @Override
    @CircuitBreaker(name = "openai")
    @Retry(name = "llm")
    public Mono<List<Float>> embed(String text) {
        Map<String, Object> body = Map.of(
                "model", config.getEmbeddingModel(),
                "input", text
        );
        
        return webClient.post()
                .uri(EMBEDDINGS_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(config.getTimeout())
                .map(json -> {
                    JsonNode data = json.get("data").get(0);
                    JsonNode embedding = data.get("embedding");
                    List<Float> result = new ArrayList<>();
                    for (JsonNode val : embedding) {
                        result.add(val.floatValue());
                    }
                    return result;
                });
    }

    @Override
    public Mono<List<List<Float>>> embedBatch(List<String> texts) {
        Map<String, Object> body = Map.of(
                "model", config.getEmbeddingModel(),
                "input", texts
        );
        
        return webClient.post()
                .uri(EMBEDDINGS_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(config.getTimeout())
                .map(json -> {
                    List<List<Float>> results = new ArrayList<>();
                    for (JsonNode item : json.get("data")) {
                        List<Float> embedding = new ArrayList<>();
                        for (JsonNode val : item.get("embedding")) {
                            embedding.add(val.floatValue());
                        }
                        results.add(embedding);
                    }
                    return results;
                });
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return webClient.get()
                .uri("/models")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(5))
                .map(json -> true)
                .onErrorReturn(false);
    }

    private Map<String, Object> buildRequestBody(LLMRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        
        body.put("model", request.getModel() != null ? request.getModel() : config.getModel());
        body.put("stream", stream);
        
        // Build messages
        List<Map<String, Object>> messages = request.getMessages().stream()
                .map(this::convertMessage)
                .collect(Collectors.toList());
        body.put("messages", messages);
        
        // Temperature
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        
        // Max tokens
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        } else {
            body.put("max_tokens", config.getMaxTokens());
        }
        
        // Stop sequences
        if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
            body.put("stop", request.getStopSequences());
        }
        
        // Tools
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = request.getTools().stream()
                    .map(this::convertTool)
                    .collect(Collectors.toList());
            body.put("tools", tools);
        }
        
        // Tool choice
        if (request.getToolChoice() != null) {
            if ("none".equals(request.getToolChoice().getType())) {
                body.put("tool_choice", "none");
            } else if ("auto".equals(request.getToolChoice().getType())) {
                body.put("tool_choice", "auto");
            } else if ("required".equals(request.getToolChoice().getType())) {
                body.put("tool_choice", "required");
            }
        }
        
        // Response format
        if (request.getResponseFormat() != null) {
            body.put("response_format", Map.of("type", request.getResponseFormat().getType()));
        }
        
        // User
        if (request.getUser() != null) {
            body.put("user", request.getUser());
        }
        
        return body;
    }

    private Map<String, Object> convertMessage(LLMRequest.Message message) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", message.getRole());
        
        if (message.getContent() != null) {
            msg.put("content", message.getContent());
        }
        
        if (message.getName() != null) {
            msg.put("name", message.getName());
        }
        
        if (message.getToolCallId() != null) {
            msg.put("tool_call_id", message.getToolCallId());
        }
        
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<Map<String, Object>> toolCalls = message.getToolCalls().stream()
                    .map(tc -> {
                        Map<String, Object> call = new HashMap<>();
                        call.put("id", tc.getId());
                        call.put("type", "function");
                        call.put("function", Map.of(
                                "name", tc.getFunction().getName(),
                                "arguments", tc.getFunction().getArguments()
                        ));
                        return call;
                    })
                    .collect(Collectors.toList());
            msg.put("tool_calls", toolCalls);
        }
        
        return msg;
    }

    private Map<String, Object> convertTool(LLMRequest.Tool tool) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", tool.getFunction().getName(),
                        "description", tool.getFunction().getDescription(),
                        "parameters", tool.getFunction().getParameters()
                )
        );
    }

    private LLMResponse parseResponse(JsonNode json, long startTime) {
        JsonNode choice = json.get("choices").get(0);
        JsonNode message = choice.get("message");
        
        String content = message.has("content") && !message.get("content").isNull() 
                ? message.get("content").asText() 
                : null;
        
        List<LLMRequest.ToolCall> toolCalls = null;
        if (message.has("tool_calls") && !message.get("tool_calls").isNull()) {
            toolCalls = new ArrayList<>();
            for (JsonNode tc : message.get("tool_calls")) {
                toolCalls.add(LLMRequest.ToolCall.builder()
                        .id(tc.get("id").asText())
                        .type("function")
                        .function(LLMRequest.FunctionCall.builder()
                                .name(tc.get("function").get("name").asText())
                                .arguments(tc.get("function").get("arguments").asText())
                                .build())
                        .build());
            }
        }
        
        LLMResponse.FinishReason finishReason = parseFinishReason(choice.get("finish_reason").asText());
        
        LLMResponse.Usage usage = null;
        if (json.has("usage")) {
            JsonNode usageNode = json.get("usage");
            usage = LLMResponse.Usage.builder()
                    .promptTokens(usageNode.get("prompt_tokens").asInt())
                    .completionTokens(usageNode.get("completion_tokens").asInt())
                    .totalTokens(usageNode.get("total_tokens").asInt())
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

    private LLMChunk parseStreamChunk(String line) {
        try {
            JsonNode json = objectMapper.readTree(line);
            
            if (!json.has("choices") || json.get("choices").isEmpty()) {
                return null;
            }
            
            JsonNode choice = json.get("choices").get(0);
            JsonNode delta = choice.get("delta");
            
            String contentDelta = delta.has("content") ? delta.get("content").asText() : null;
            
            List<LLMChunk.ToolCallDelta> toolCallDeltas = null;
            if (delta.has("tool_calls")) {
                toolCallDeltas = new ArrayList<>();
                for (JsonNode tc : delta.get("tool_calls")) {
                    LLMChunk.ToolCallDelta.ToolCallDeltaBuilder builder = LLMChunk.ToolCallDelta.builder()
                            .index(tc.get("index").asInt());
                    if (tc.has("id")) builder.id(tc.get("id").asText());
                    if (tc.has("type")) builder.type(tc.get("type").asText());
                    if (tc.has("function")) {
                        JsonNode func = tc.get("function");
                        if (func.has("name")) builder.functionName(func.get("name").asText());
                        if (func.has("arguments")) builder.argumentsDelta(func.get("arguments").asText());
                    }
                    toolCallDeltas.add(builder.build());
                }
            }
            
            boolean finished = choice.has("finish_reason") && !choice.get("finish_reason").isNull();
            LLMResponse.FinishReason finishReason = finished 
                    ? parseFinishReason(choice.get("finish_reason").asText()) 
                    : null;
            
            return LLMChunk.builder()
                    .id(json.get("id").asText())
                    .model(json.get("model").asText())
                    .contentDelta(contentDelta)
                    .toolCallDeltas(toolCallDeltas)
                    .finished(finished)
                    .finishReason(finishReason)
                    .build();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse stream chunk: {}", e.getMessage());
            return null;
        }
    }

    private LLMResponse.FinishReason parseFinishReason(String reason) {
        return switch (reason) {
            case "stop" -> LLMResponse.FinishReason.STOP;
            case "length" -> LLMResponse.FinishReason.LENGTH;
            case "tool_calls" -> LLMResponse.FinishReason.TOOL_CALLS;
            case "content_filter" -> LLMResponse.FinishReason.CONTENT_FILTER;
            default -> LLMResponse.FinishReason.STOP;
        };
    }

    @Override
    public int countTokens(String text) {
        // Approximation: ~4 characters per token for GPT models
        // For accurate counting, consider using tiktoken library
        return text != null ? (int) Math.ceil(text.length() / 4.0) : 0;
    }
}
