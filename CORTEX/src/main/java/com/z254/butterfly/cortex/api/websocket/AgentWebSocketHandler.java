package com.z254.butterfly.cortex.api.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.domain.model.AgentTask;
import com.z254.butterfly.cortex.domain.model.AgentThought;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time agent streaming.
 * Supports bidirectional communication for task submission and thought streaming.
 */
@Component
@Slf4j
public class AgentWebSocketHandler implements WebSocketHandler {

    private final AgentService agentService;
    private final ObjectMapper objectMapper;
    
    // Track active sessions
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public AgentWebSocketHandler(AgentService agentService, ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        SessionState state = new SessionState(sessionId);
        sessions.put(sessionId, state);
        
        log.info("WebSocket session opened: {}", sessionId);

        // Handle incoming messages
        Mono<Void> input = session.receive()
                .doOnNext(msg -> log.debug("Received message: {}", msg.getPayloadAsText()))
                .flatMap(msg -> handleMessage(state, msg))
                .then();

        // Send outgoing messages
        Flux<WebSocketMessage> output = state.getOutboundSink().asFlux()
                .map(payload -> session.textMessage(payload));

        // Send heartbeat
        Flux<WebSocketMessage> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(tick -> session.textMessage("{\"type\":\"heartbeat\",\"timestamp\":\"" + 
                        Instant.now() + "\"}"));

        return session.send(Flux.merge(output, heartbeat))
                .and(input)
                .doFinally(signalType -> {
                    sessions.remove(sessionId);
                    log.info("WebSocket session closed: {} - {}", sessionId, signalType);
                });
    }

    private Mono<Void> handleMessage(SessionState state, WebSocketMessage message) {
        try {
            String payload = message.getPayloadAsText();
            WebSocketRequest request = objectMapper.readValue(payload, WebSocketRequest.class);
            
            return switch (request.getType()) {
                case "subscribe" -> handleSubscribe(state, request);
                case "unsubscribe" -> handleUnsubscribe(state, request);
                case "execute" -> handleExecute(state, request);
                case "cancel" -> handleCancel(state, request);
                case "ping" -> handlePing(state);
                default -> {
                    sendError(state, "Unknown message type: " + request.getType());
                    yield Mono.empty();
                }
            };
        } catch (JsonProcessingException e) {
            log.warn("Invalid JSON message: {}", e.getMessage());
            sendError(state, "Invalid JSON: " + e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<Void> handleSubscribe(SessionState state, WebSocketRequest request) {
        String taskId = request.getTaskId();
        String conversationId = request.getConversationId();
        
        if (taskId != null) {
            state.getSubscribedTasks().add(taskId);
            sendAck(state, "Subscribed to task: " + taskId);
        }
        if (conversationId != null) {
            state.getSubscribedConversations().add(conversationId);
            sendAck(state, "Subscribed to conversation: " + conversationId);
        }
        
        return Mono.empty();
    }

    private Mono<Void> handleUnsubscribe(SessionState state, WebSocketRequest request) {
        String taskId = request.getTaskId();
        String conversationId = request.getConversationId();
        
        if (taskId != null) {
            state.getSubscribedTasks().remove(taskId);
            sendAck(state, "Unsubscribed from task: " + taskId);
        }
        if (conversationId != null) {
            state.getSubscribedConversations().remove(conversationId);
            sendAck(state, "Unsubscribed from conversation: " + conversationId);
        }
        
        return Mono.empty();
    }

    private Mono<Void> handleExecute(SessionState state, WebSocketRequest request) {
        String agentId = request.getAgentId();
        String input = request.getInput();
        String conversationId = request.getConversationId();
        
        if (agentId == null || input == null) {
            sendError(state, "agentId and input are required");
            return Mono.empty();
        }

        String taskId = UUID.randomUUID().toString();
        
        AgentTask task = AgentTask.builder()
                .id(taskId)
                .agentId(agentId)
                .conversationId(conversationId)
                .input(input)
                .correlationId(UUID.randomUUID().toString())
                .status(AgentTask.TaskStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        // Auto-subscribe to this task
        state.getSubscribedTasks().add(taskId);
        
        // Send task created acknowledgment
        sendTaskStarted(state, taskId, agentId);

        // Execute and stream thoughts
        return agentService.executeTaskStreaming(task)
                .doOnNext(thought -> sendThought(state, thought))
                .doOnComplete(() -> sendTaskCompleted(state, taskId))
                .doOnError(e -> sendError(state, "Task failed: " + e.getMessage()))
                .then();
    }

    private Mono<Void> handleCancel(SessionState state, WebSocketRequest request) {
        String taskId = request.getTaskId();
        if (taskId == null) {
            sendError(state, "taskId is required for cancel");
            return Mono.empty();
        }

        return agentService.cancelTask(taskId, "Cancelled via WebSocket")
                .doOnSuccess(task -> sendAck(state, "Task cancelled: " + taskId))
                .doOnError(e -> sendError(state, "Cancel failed: " + e.getMessage()))
                .then();
    }

    private Mono<Void> handlePing(SessionState state) {
        String pong = "{\"type\":\"pong\",\"timestamp\":\"" + Instant.now() + "\"}";
        state.getOutboundSink().tryEmitNext(pong);
        return Mono.empty();
    }

    private void sendThought(SessionState state, AgentThought thought) {
        if (state.getSubscribedTasks().contains(thought.getTaskId())) {
            try {
                WebSocketResponse response = WebSocketResponse.builder()
                        .type("thought")
                        .taskId(thought.getTaskId())
                        .data(thought)
                        .timestamp(Instant.now())
                        .build();
                String json = objectMapper.writeValueAsString(response);
                state.getOutboundSink().tryEmitNext(json);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize thought: {}", e.getMessage());
            }
        }
    }

    private void sendTaskStarted(SessionState state, String taskId, String agentId) {
        try {
            WebSocketResponse response = WebSocketResponse.builder()
                    .type("task_started")
                    .taskId(taskId)
                    .data(Map.of("agentId", agentId))
                    .timestamp(Instant.now())
                    .build();
            String json = objectMapper.writeValueAsString(response);
            state.getOutboundSink().tryEmitNext(json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize task started: {}", e.getMessage());
        }
    }

    private void sendTaskCompleted(SessionState state, String taskId) {
        try {
            WebSocketResponse response = WebSocketResponse.builder()
                    .type("task_completed")
                    .taskId(taskId)
                    .timestamp(Instant.now())
                    .build();
            String json = objectMapper.writeValueAsString(response);
            state.getOutboundSink().tryEmitNext(json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize task completed: {}", e.getMessage());
        }
    }

    private void sendAck(SessionState state, String message) {
        try {
            WebSocketResponse response = WebSocketResponse.builder()
                    .type("ack")
                    .data(Map.of("message", message))
                    .timestamp(Instant.now())
                    .build();
            String json = objectMapper.writeValueAsString(response);
            state.getOutboundSink().tryEmitNext(json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize ack: {}", e.getMessage());
        }
    }

    private void sendError(SessionState state, String error) {
        try {
            WebSocketResponse response = WebSocketResponse.builder()
                    .type("error")
                    .error(error)
                    .timestamp(Instant.now())
                    .build();
            String json = objectMapper.writeValueAsString(response);
            state.getOutboundSink().tryEmitNext(json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize error: {}", e.getMessage());
        }
    }

    /**
     * Broadcast a thought to all subscribed sessions.
     */
    public void broadcastThought(AgentThought thought) {
        sessions.values().forEach(state -> sendThought(state, thought));
    }

    /**
     * WebSocket request structure.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class WebSocketRequest {
        private String type;
        private String taskId;
        private String conversationId;
        private String agentId;
        private String input;
        private Map<String, Object> context;
    }

    /**
     * WebSocket response structure.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class WebSocketResponse {
        private String type;
        private String taskId;
        private Object data;
        private String error;
        private Instant timestamp;
    }

    /**
     * Session state tracking.
     */
    private static class SessionState {
        private final String sessionId;
        private final java.util.Set<String> subscribedTasks = ConcurrentHashMap.newKeySet();
        private final java.util.Set<String> subscribedConversations = ConcurrentHashMap.newKeySet();
        private final Sinks.Many<String> outboundSink = Sinks.many().multicast().onBackpressureBuffer();

        SessionState(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public java.util.Set<String> getSubscribedTasks() {
            return subscribedTasks;
        }

        public java.util.Set<String> getSubscribedConversations() {
            return subscribedConversations;
        }

        public Sinks.Many<String> getOutboundSink() {
            return outboundSink;
        }
    }
}
