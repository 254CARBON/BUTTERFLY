package com.z254.butterfly.cortex.llm.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.llm.*;
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
 * Ollama LLM Provider implementation.
 * Supports local models like Llama, Mistral, CodeLlama, etc.
 * Useful for air-gapped deployments or cost-sensitive scenarios.
 */
@Component
@ConditionalOnProperty(prefix = "cortex.llm.ollama", name = "enabled", havingValue = "true")
@Slf4j
public class OllamaProvider implements LLMProvider {

    private static final String PROVIDER_ID = "ollama";
    private static final String CHAT_PATH = "/api/chat";
    private static final String EMBEDDINGS_PATH = "/api/embeddings";
    private static final String TAGS_PATH = "/api/tags";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final CortexProperties.LLMProperties.OllamaProperties config;
    private final Timer llmCallTimer;
    private final Counter llmCallCounter;
    private final Counter llmErrorCounter;

    public OllamaProvider(
            CortexProperties cortexProperties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.config = cortexProperties.getLlm().getOllama();
        this.objectMapper = objectMapper;
        
        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
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
        // Ollama tool support depends on the model
        // Most recent models (llama3, mistral) support tools
        return true;
    }

    @Override
    public String getDefaultModel() {
        return config.getModel();
    }

    @Override
    public Mono<LLMResponse> complete(LLMRequest request) {
        llmCallCounter.increment();
        long startTime = System.currentTimeMillis();
        
        Map<String, Object> body = buildRequestBody(request, false);
        
        return webClient.post()
                .uri(CHAT_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(config.getTimeout())
                .map(json -> parseResponse(json, startTime))
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    llmCallTimer.record(Duration.ofMillis(duration));
                    log.debug("Ollama completion: {}ms", duration);
                })
                .doOnError(e -> {
                    llmErrorCounter.increment();
                    log.error("Ollama completion error: {}", e.getMessage());
                });
    }

    @Override
    public Flux<LLMChunk> stream(LLMRequest request) {
        llmCallCounter.increment();
        
        Map<String, Object> body = buildRequestBody(request, true);
        
        return webClient.post()
                .uri(CHAT_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isEmpty())
                .map(this::parseStreamChunk)
                .filter(Objects::nonNull)
                .doOnError(e -> {
                    llmErrorCounter.increment();
                    log.error("Ollama streaming error: {}", e.getMessage());
                });
    }

    @Override
    public Mono<List<Float>> embed(String text) {
        Map<String, Object> body = Map.of(
                "model", config.getEmbeddingModel(),
                "prompt", text
        );
        
        return webClient.post()
                .uri(EMBEDDINGS_PATH)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(config.getTimeout())
                .map(json -> {
                    JsonNode embedding = json.get("embedding");
                    List<Float> result = new ArrayList<>();
                    for (JsonNode val : embedding) {
                        result.add(val.floatValue());
                    }
                    return result;
                });
    }

    @Override
    public Mono<List<List<Float>>> embedBatch(List<String> texts) {
        // Ollama doesn't support batch embeddings, so we do them sequentially
        return Flux.fromIterable(texts)
                .flatMap(this::embed)
                .collectList();
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return webClient.get()
                .uri(TAGS_PATH)
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
        
        // Options
        Map<String, Object> options = new HashMap<>();
        if (request.getTemperature() != null) {
            options.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            options.put("num_predict", request.getMaxTokens());
        }
        if (request.getTopP() != null) {
            options.put("top_p", request.getTopP());
        }
        if (request.getStopSequences() != null) {
            options.put("stop", request.getStopSequences());
        }
        if (!options.isEmpty()) {
            body.put("options", options);
        }
        
        // Tools (if model supports them)
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
        msg.put("role", message.getRole());
        msg.put("content", message.getContent() != null ? message.getContent() : "");
        
        // Handle tool calls for assistant messages
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<Map<String, Object>> toolCalls = message.getToolCalls().stream()
                    .map(tc -> {
                        try {
                            return Map.of(
                                    "function", Map.of(
                                            "name", tc.getFunction().getName(),
                                            "arguments", objectMapper.readValue(
                                                    tc.getFunction().getArguments(), Map.class)
                                    )
                            );
                        } catch (JsonProcessingException e) {
                            return Map.of(
                                    "function", Map.of(
                                            "name", tc.getFunction().getName(),
                                            "arguments", Map.of()
                                    )
                            );
                        }
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
        JsonNode message = json.get("message");
        String content = message.has("content") ? message.get("content").asText() : null;
        
        List<LLMRequest.ToolCall> toolCalls = null;
        if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
            toolCalls = new ArrayList<>();
            int index = 0;
            for (JsonNode tc : message.get("tool_calls")) {
                try {
                    JsonNode function = tc.get("function");
                    toolCalls.add(LLMRequest.ToolCall.builder()
                            .id("call_" + index++)
                            .type("function")
                            .function(LLMRequest.FunctionCall.builder()
                                    .name(function.get("name").asText())
                                    .arguments(objectMapper.writeValueAsString(function.get("arguments")))
                                    .build())
                            .build());
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize tool arguments: {}", e.getMessage());
                }
            }
            if (toolCalls.isEmpty()) toolCalls = null;
        }
        
        LLMResponse.FinishReason finishReason = LLMResponse.FinishReason.STOP;
        if (json.has("done_reason")) {
            String reason = json.get("done_reason").asText();
            if ("length".equals(reason)) {
                finishReason = LLMResponse.FinishReason.LENGTH;
            }
        }
        if (toolCalls != null) {
            finishReason = LLMResponse.FinishReason.TOOL_CALLS;
        }
        
        // Ollama provides eval_count for tokens
        LLMResponse.Usage usage = null;
        if (json.has("prompt_eval_count") || json.has("eval_count")) {
            int promptTokens = json.has("prompt_eval_count") ? json.get("prompt_eval_count").asInt() : 0;
            int completionTokens = json.has("eval_count") ? json.get("eval_count").asInt() : 0;
            usage = LLMResponse.Usage.builder()
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .build();
        }
        
        return LLMResponse.builder()
                .id(UUID.randomUUID().toString())
                .model(json.has("model") ? json.get("model").asText() : config.getModel())
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
            
            boolean done = json.has("done") && json.get("done").asBoolean();
            
            JsonNode message = json.get("message");
            String contentDelta = null;
            if (message != null && message.has("content")) {
                contentDelta = message.get("content").asText();
            }
            
            if (done) {
                LLMResponse.Usage usage = null;
                if (json.has("prompt_eval_count") || json.has("eval_count")) {
                    int promptTokens = json.has("prompt_eval_count") ? json.get("prompt_eval_count").asInt() : 0;
                    int completionTokens = json.has("eval_count") ? json.get("eval_count").asInt() : 0;
                    usage = LLMResponse.Usage.builder()
                            .promptTokens(promptTokens)
                            .completionTokens(completionTokens)
                            .totalTokens(promptTokens + completionTokens)
                            .build();
                }
                return LLMChunk.finished(null, config.getModel(), LLMResponse.FinishReason.STOP, usage);
            }
            
            if (contentDelta != null && !contentDelta.isEmpty()) {
                return LLMChunk.content(null, config.getModel(), contentDelta);
            }
            
            return null;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Ollama stream chunk: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public int countTokens(String text) {
        // Rough approximation
        return text != null ? (int) Math.ceil(text.length() / 4.0) : 0;
    }
}
