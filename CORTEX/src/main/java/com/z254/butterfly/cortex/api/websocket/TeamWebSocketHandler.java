package com.z254.butterfly.cortex.api.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.orchestration.AgentMessageBus;
import com.z254.butterfly.cortex.orchestration.MultiAgentOrchestrator;
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
 * WebSocket handler for real-time multi-agent team streaming.
 * Supports bidirectional communication for team task submission, thought streaming,
 * and inter-agent message observation.
 * 
 * Message Types (client -> server):
 * - subscribe: Subscribe to a team task's thoughts and messages
 * - unsubscribe: Unsubscribe from a team task
 * - execute_team: Submit and execute a team task
 * - cancel: Cancel a running team task
 * - ping: Heartbeat ping
 * 
 * Message Types (server -> client):
 * - team_thought: A thought from an agent in the team
 * - agent_message: An inter-agent message
 * - task_started: Team task execution started
 * - task_completed: Team task completed
 * - task_failed: Team task failed
 * - ack: Acknowledgment
 * - error: Error message
 * - pong: Heartbeat response
 */
@Component
@Slf4j
public class TeamWebSocketHandler implements WebSocketHandler {

    private final MultiAgentOrchestrator orchestrator;
    private final AgentMessageBus messageBus;
    private final ObjectMapper objectMapper;
    
    // Track active sessions
    private final Map<String, TeamSessionState> sessions = new ConcurrentHashMap<>();

    public TeamWebSocketHandler(MultiAgentOrchestrator orchestrator, 
                                 AgentMessageBus messageBus,
                                 ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.messageBus = messageBus;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        TeamSessionState state = new TeamSessionState(sessionId);
        sessions.put(sessionId, state);
        
        log.info("Team WebSocket session opened: {}", sessionId);

        // Handle incoming messages
        Mono<Void> input = session.receive()
                .doOnNext(msg -> log.debug("Team WS received: {}", msg.getPayloadAsText()))
                .flatMap(msg -> handleMessage(state, msg))
                .then();

        // Send outgoing messages
        Flux<WebSocketMessage> output = state.getOutboundSink().asFlux()
                .map(payload -> session.textMessage(payload));

        // Send heartbeat
        Flux<WebSocketMessage> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(tick -> session.textMessage(
                        "{\"type\":\"heartbeat\",\"timestamp\":\"" + Instant.now() + "\"}"));

        return session.send(Flux.merge(output, heartbeat))
                .and(input)
                .doFinally(signalType -> {
                    sessions.remove(sessionId);
                    state.cleanup();
                    log.info("Team WebSocket session closed: {} - {}", sessionId, signalType);
                });
    }

    private Mono<Void> handleMessage(TeamSessionState state, WebSocketMessage message) {
        try {
            String payload = message.getPayloadAsText();
            TeamWebSocketRequest request = objectMapper.readValue(payload, TeamWebSocketRequest.class);
            
            return switch (request.getType()) {
                case "subscribe" -> handleSubscribe(state, request);
                case "unsubscribe" -> handleUnsubscribe(state, request);
                case "execute_team" -> handleExecuteTeam(state, request);
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

    private Mono<Void> handleSubscribe(TeamSessionState state, TeamWebSocketRequest request) {
        String taskId = request.getTaskId();
        String teamId = request.getTeamId();
        
        if (taskId != null) {
            state.getSubscribedTasks().add(taskId);
            
            // Subscribe to inter-agent messages
            messageBus.subscribeToTask(taskId)
                    .doOnNext(msg -> sendAgentMessage(state, msg))
                    .subscribe();
            
            sendAck(state, "Subscribed to team task: " + taskId);
        }
        
        if (teamId != null) {
            state.getSubscribedTeams().add(teamId);
            sendAck(state, "Subscribed to team: " + teamId);
        }
        
        return Mono.empty();
    }

    private Mono<Void> handleUnsubscribe(TeamSessionState state, TeamWebSocketRequest request) {
        String taskId = request.getTaskId();
        String teamId = request.getTeamId();
        
        if (taskId != null) {
            state.getSubscribedTasks().remove(taskId);
            sendAck(state, "Unsubscribed from team task: " + taskId);
        }
        
        if (teamId != null) {
            state.getSubscribedTeams().remove(teamId);
            sendAck(state, "Unsubscribed from team: " + teamId);
        }
        
        return Mono.empty();
    }

    private Mono<Void> handleExecuteTeam(TeamSessionState state, TeamWebSocketRequest request) {
        String teamId = request.getTeamId();
        String input = request.getInput();
        
        if (teamId == null || input == null) {
            sendError(state, "teamId and input are required");
            return Mono.empty();
        }

        String taskId = UUID.randomUUID().toString();
        String userId = request.getUserId() != null ? request.getUserId() : "websocket-user";
        
        TeamTask task = TeamTask.builder()
                .id(taskId)
                .teamId(teamId)
                .input(input)
                .userId(userId)
                .context(request.getContext())
                .status(TeamTaskStatus.PENDING)
                .createdAt(Instant.now())
                .correlationId(UUID.randomUUID().toString())
                .build();

        // Auto-subscribe to this task
        state.getSubscribedTasks().add(taskId);
        
        // Send task created acknowledgment
        sendTaskStarted(state, taskId, teamId);

        // Execute and stream thoughts
        return orchestrator.executeTaskStreaming(task)
                .doOnNext(thought -> sendTeamThought(state, thought))
                .doOnComplete(() -> {
                    orchestrator.getTaskResult(taskId)
                            .subscribe(result -> sendTaskCompleted(state, taskId, result));
                })
                .doOnError(e -> sendTaskFailed(state, taskId, e.getMessage()))
                .then();
    }

    private Mono<Void> handleCancel(TeamSessionState state, TeamWebSocketRequest request) {
        String taskId = request.getTaskId();
        if (taskId == null) {
            sendError(state, "taskId is required for cancel");
            return Mono.empty();
        }

        return orchestrator.cancelTask(taskId, "Cancelled via WebSocket")
                .doOnSuccess(task -> sendAck(state, "Team task cancelled: " + taskId))
                .doOnError(e -> sendError(state, "Cancel failed: " + e.getMessage()))
                .then();
    }

    private Mono<Void> handlePing(TeamSessionState state) {
        String pong = "{\"type\":\"pong\",\"timestamp\":\"" + Instant.now() + "\"}";
        state.getOutboundSink().tryEmitNext(pong);
        return Mono.empty();
    }

    private void sendTeamThought(TeamSessionState state, TeamThought thought) {
        if (state.getSubscribedTasks().contains(thought.getTeamTaskId())) {
            try {
                TeamWebSocketResponse response = TeamWebSocketResponse.builder()
                        .type("team_thought")
                        .taskId(thought.getTeamTaskId())
                        .data(Map.of(
                                "id", thought.getId(),
                                "agentId", thought.getAgentId() != null ? thought.getAgentId() : "",
                                "agentName", thought.getAgentName() != null ? thought.getAgentName() : "",
                                "agentRole", thought.getAgentRole() != null ? thought.getAgentRole().name() : "",
                                "type", thought.getType().name(),
                                "content", thought.getContent() != null ? thought.getContent() : "",
                                "phase", thought.getPhase() != null ? thought.getPhase() : "",
                                "iteration", thought.getIteration(),
                                "round", thought.getRound(),
                                "confidence", thought.getConfidence() != null ? thought.getConfidence() : 0.0,
                                "tokenCount", thought.getTokenCount() != null ? thought.getTokenCount() : 0
                        ))
                        .timestamp(thought.getTimestamp())
                        .build();
                String json = objectMapper.writeValueAsString(response);
                state.getOutboundSink().tryEmitNext(json);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize team thought: {}", e.getMessage());
            }
        }
    }

    private void sendAgentMessage(TeamSessionState state, AgentMessage message) {
        if (state.getSubscribedTasks().contains(message.getTeamTaskId())) {
            try {
                TeamWebSocketResponse response = TeamWebSocketResponse.builder()
                        .type("agent_message")
                        .taskId(message.getTeamTaskId())
                        .data(Map.of(
                                "id", message.getId(),
                                "fromAgentId", message.getFromAgentId() != null ? message.getFromAgentId() : "",
                                "toAgentId", message.getToAgentId() != null ? message.getToAgentId() : "",
                                "messageType", message.getType().name(),
                                "content", message.getContent() != null ? message.getContent() : "",
                                "round", message.getRound(),
                                "confidence", message.getConfidence()
                        ))
                        .timestamp(message.getTimestamp())
                        .build();
                String json = objectMapper.writeValueAsString(response);
                state.getOutboundSink().tryEmitNext(json);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize agent message: {}", e.getMessage());
            }
        }
    }

    private void sendTaskStarted(TeamSessionState state, String taskId, String teamId) {
        try {
            TeamWebSocketResponse response = TeamWebSocketResponse.builder()
                    .type("task_started")
                    .taskId(taskId)
                    .data(Map.of("teamId", teamId))
                    .timestamp(Instant.now())
                    .build();
            String json = objectMapper.writeValueAsString(response);
            state.getOutboundSink().tryEmitNext(json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize task started: {}", e.getMessage());
        }
    }

    private void sendTaskCompleted(TeamSessionState state, String taskId, TeamResult result) {
        try {
            TeamWebSocketResponse response = TeamWebSocketResponse.builder()
                    .type("task_completed")
                    .taskId(taskId)
                    .data(Map.of(
                            "status", result.getStatus().name(),
                            "output", result.getOutput() != null ? result.getOutput() : "",
                            "confidence", result.getConfidence(),
                            "agentCount", result.getAgentCount(),
                            "totalTokens", result.getTotalTokens(),
                            "durationMs", result.getDuration() != null ? result.getDuration().toMillis() : 0
                    ))
                    .timestamp(Instant.now())
                    .build();
            String json = objectMapper.writeValueAsString(response);
            state.getOutboundSink().tryEmitNext(json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize task completed: {}", e.getMessage());
        }
    }

    private void sendTaskFailed(TeamSessionState state, String taskId, String error) {
        try {
            TeamWebSocketResponse response = TeamWebSocketResponse.builder()
                    .type("task_failed")
                    .taskId(taskId)
                    .error(error)
                    .timestamp(Instant.now())
                    .build();
            String json = objectMapper.writeValueAsString(response);
            state.getOutboundSink().tryEmitNext(json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize task failed: {}", e.getMessage());
        }
    }

    private void sendAck(TeamSessionState state, String message) {
        try {
            TeamWebSocketResponse response = TeamWebSocketResponse.builder()
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

    private void sendError(TeamSessionState state, String error) {
        try {
            TeamWebSocketResponse response = TeamWebSocketResponse.builder()
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
     * Broadcast a team thought to all subscribed sessions.
     */
    public void broadcastTeamThought(TeamThought thought) {
        sessions.values().forEach(state -> sendTeamThought(state, thought));
    }

    /**
     * Broadcast an agent message to all subscribed sessions.
     */
    public void broadcastAgentMessage(AgentMessage message) {
        sessions.values().forEach(state -> sendAgentMessage(state, message));
    }

    /**
     * Get active session count.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * WebSocket request structure for team operations.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TeamWebSocketRequest {
        private String type;
        private String taskId;
        private String teamId;
        private String input;
        private String userId;
        private Map<String, Object> context;
    }

    /**
     * WebSocket response structure for team operations.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class TeamWebSocketResponse {
        private String type;
        private String taskId;
        private Object data;
        private String error;
        private Instant timestamp;
    }

    /**
     * Session state for team WebSocket connections.
     */
    private static class TeamSessionState {
        private final String sessionId;
        private final java.util.Set<String> subscribedTasks = ConcurrentHashMap.newKeySet();
        private final java.util.Set<String> subscribedTeams = ConcurrentHashMap.newKeySet();
        private final Sinks.Many<String> outboundSink = Sinks.many().multicast().onBackpressureBuffer();

        TeamSessionState(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public java.util.Set<String> getSubscribedTasks() {
            return subscribedTasks;
        }

        public java.util.Set<String> getSubscribedTeams() {
            return subscribedTeams;
        }

        public Sinks.Many<String> getOutboundSink() {
            return outboundSink;
        }

        public void cleanup() {
            subscribedTasks.clear();
            subscribedTeams.clear();
            outboundSink.tryEmitComplete();
        }
    }
}
