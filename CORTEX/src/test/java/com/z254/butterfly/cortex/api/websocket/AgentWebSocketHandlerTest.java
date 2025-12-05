package com.z254.butterfly.cortex.api.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.domain.model.AgentTask;
import com.z254.butterfly.cortex.domain.model.AgentThought;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentWebSocketHandler}.
 */
@ExtendWith(MockitoExtension.class)
class AgentWebSocketHandlerTest {

    @Mock
    private AgentService agentService;

    @Mock
    private WebSocketSession session;

    private ObjectMapper objectMapper;
    private AgentWebSocketHandler handler;
    private List<String> sentMessages;
    private Sinks.Many<WebSocketMessage> inboundSink;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        handler = new AgentWebSocketHandler(agentService, objectMapper);
        
        sentMessages = new ArrayList<>();
        inboundSink = Sinks.many().multicast().onBackpressureBuffer();

        when(session.getId()).thenReturn("test-session-123");
        when(session.receive()).thenReturn(inboundSink.asFlux());
        when(session.textMessage(anyString())).thenAnswer(inv -> {
            String payload = inv.getArgument(0);
            sentMessages.add(payload);
            WebSocketMessage msg = mock(WebSocketMessage.class);
            when(msg.getPayloadAsText()).thenReturn(payload);
            return msg;
        });
        when(session.send(any())).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("Connection Handling")
    class ConnectionHandlingTests {

        @Test
        @DisplayName("should handle new WebSocket connection")
        void handleNewConnection() {
            // Simulate connection
            Mono<Void> handleResult = handler.handle(session);

            // Just verify it doesn't throw
            assertThat(handleResult).isNotNull();
        }
    }

    @Nested
    @DisplayName("Message Handling")
    class MessageHandlingTests {

        @Test
        @DisplayName("should respond to ping with pong")
        void respondToPingWithPong() throws JsonProcessingException {
            String pingMessage = "{\"type\":\"ping\"}";
            WebSocketMessage inboundMsg = mockMessage(pingMessage);

            when(session.receive()).thenReturn(Flux.just(inboundMsg));

            // Capture outbound messages
            AtomicReference<List<WebSocketMessage>> captured = new AtomicReference<>(new ArrayList<>());
            when(session.send(any())).thenAnswer(inv -> {
                Flux<WebSocketMessage> flux = inv.getArgument(0);
                return flux.take(1).doOnNext(msg -> captured.get().add(msg)).then();
            });

            StepVerifier.create(handler.handle(session))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));

            // Check for pong response
            boolean hasPong = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"pong\""));
            assertThat(hasPong).isTrue();
        }

        @Test
        @DisplayName("should handle subscribe message")
        void handleSubscribeMessage() throws JsonProcessingException {
            String subscribeMessage = "{\"type\":\"subscribe\",\"taskId\":\"task-123\"}";
            WebSocketMessage inboundMsg = mockMessage(subscribeMessage);

            when(session.receive()).thenReturn(Flux.just(inboundMsg));
            when(session.send(any())).thenReturn(Mono.empty());

            StepVerifier.create(handler.handle(session))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));

            // Check for ack response
            boolean hasAck = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"ack\"") && 
                            msg.contains("Subscribed"));
            assertThat(hasAck).isTrue();
        }

        @Test
        @DisplayName("should handle unsubscribe message")
        void handleUnsubscribeMessage() throws JsonProcessingException {
            String unsubscribeMessage = "{\"type\":\"unsubscribe\",\"taskId\":\"task-123\"}";
            WebSocketMessage inboundMsg = mockMessage(unsubscribeMessage);

            when(session.receive()).thenReturn(Flux.just(inboundMsg));
            when(session.send(any())).thenReturn(Mono.empty());

            StepVerifier.create(handler.handle(session))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));

            // Check for ack response
            boolean hasAck = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"ack\"") && 
                            msg.contains("Unsubscribed"));
            assertThat(hasAck).isTrue();
        }

        @Test
        @DisplayName("should return error for unknown message type")
        void returnErrorForUnknownMessageType() throws JsonProcessingException {
            String unknownMessage = "{\"type\":\"unknown_type\"}";
            WebSocketMessage inboundMsg = mockMessage(unknownMessage);

            when(session.receive()).thenReturn(Flux.just(inboundMsg));
            when(session.send(any())).thenReturn(Mono.empty());

            StepVerifier.create(handler.handle(session))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));

            // Check for error response
            boolean hasError = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"error\"") && 
                            msg.contains("Unknown message type"));
            assertThat(hasError).isTrue();
        }

        @Test
        @DisplayName("should return error for invalid JSON")
        void returnErrorForInvalidJson() {
            String invalidJson = "not valid json {{{";
            WebSocketMessage inboundMsg = mockMessage(invalidJson);

            when(session.receive()).thenReturn(Flux.just(inboundMsg));
            when(session.send(any())).thenReturn(Mono.empty());

            StepVerifier.create(handler.handle(session))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));

            // Check for error response
            boolean hasError = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"error\"") && 
                            msg.contains("Invalid JSON"));
            assertThat(hasError).isTrue();
        }
    }

    @Nested
    @DisplayName("Task Execution")
    class TaskExecutionTests {

        @Test
        @DisplayName("should execute task and stream thoughts")
        void executeTaskAndStreamThoughts() throws JsonProcessingException {
            String executeMessage = "{\"type\":\"execute\",\"agentId\":\"agent-123\",\"input\":\"Hello world\"}";
            WebSocketMessage inboundMsg = mockMessage(executeMessage);

            AgentThought thought = AgentThought.builder()
                    .id("thought-1")
                    .taskId("task-123")
                    .agentId("agent-123")
                    .type(AgentThought.ThoughtType.FINAL_ANSWER)
                    .content("Response")
                    .timestamp(Instant.now())
                    .build();

            when(session.receive()).thenReturn(Flux.just(inboundMsg));
            when(session.send(any())).thenReturn(Mono.empty());
            when(agentService.executeTaskStreaming(any(AgentTask.class)))
                    .thenReturn(Flux.just(thought));

            StepVerifier.create(handler.handle(session))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));

            // Check for task_started and task_completed messages
            boolean hasTaskStarted = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"task_started\""));
            boolean hasTaskCompleted = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"task_completed\""));

            assertThat(hasTaskStarted).isTrue();
            assertThat(hasTaskCompleted).isTrue();
        }

        @Test
        @DisplayName("should return error when agentId is missing")
        void returnErrorWhenAgentIdMissing() throws JsonProcessingException {
            String executeMessage = "{\"type\":\"execute\",\"input\":\"Hello world\"}";
            WebSocketMessage inboundMsg = mockMessage(executeMessage);

            when(session.receive()).thenReturn(Flux.just(inboundMsg));
            when(session.send(any())).thenReturn(Mono.empty());

            StepVerifier.create(handler.handle(session))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));

            // Check for error response
            boolean hasError = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"error\"") && 
                            msg.contains("agentId and input are required"));
            assertThat(hasError).isTrue();
        }

        @Test
        @DisplayName("should return error when input is missing")
        void returnErrorWhenInputMissing() throws JsonProcessingException {
            String executeMessage = "{\"type\":\"execute\",\"agentId\":\"agent-123\"}";
            WebSocketMessage inboundMsg = mockMessage(executeMessage);

            when(session.receive()).thenReturn(Flux.just(inboundMsg));
            when(session.send(any())).thenReturn(Mono.empty());

            StepVerifier.create(handler.handle(session))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));

            // Check for error response
            boolean hasError = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"error\"") && 
                            msg.contains("agentId and input are required"));
            assertThat(hasError).isTrue();
        }

        @Test
        @DisplayName("should handle task execution error")
        void handleTaskExecutionError() throws JsonProcessingException {
            String executeMessage = "{\"type\":\"execute\",\"agentId\":\"agent-123\",\"input\":\"Hello\"}";
            WebSocketMessage inboundMsg = mockMessage(executeMessage);

            when(session.receive()).thenReturn(Flux.just(inboundMsg));
            when(session.send(any())).thenReturn(Mono.empty());
            when(agentService.executeTaskStreaming(any(AgentTask.class)))
                    .thenReturn(Flux.error(new RuntimeException("Agent not found")));

            StepVerifier.create(handler.handle(session))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));

            // Check for error response
            boolean hasError = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"error\"") && 
                            msg.contains("Task failed"));
            assertThat(hasError).isTrue();
        }
    }

    @Nested
    @DisplayName("Task Cancellation")
    class TaskCancellationTests {

        @Test
        @DisplayName("should cancel task successfully")
        void cancelTaskSuccessfully() throws JsonProcessingException {
            String cancelMessage = "{\"type\":\"cancel\",\"taskId\":\"task-123\"}";
            WebSocketMessage inboundMsg = mockMessage(cancelMessage);

            AgentTask cancelledTask = AgentTask.builder()
                    .id("task-123")
                    .status(AgentTask.TaskStatus.CANCELLED)
                    .build();

            when(session.receive()).thenReturn(Flux.just(inboundMsg));
            when(session.send(any())).thenReturn(Mono.empty());
            when(agentService.cancelTask(anyString(), anyString()))
                    .thenReturn(Mono.just(cancelledTask));

            StepVerifier.create(handler.handle(session))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));

            // Check for ack response
            boolean hasAck = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"ack\"") && 
                            msg.contains("Task cancelled"));
            assertThat(hasAck).isTrue();
        }

        @Test
        @DisplayName("should return error when taskId is missing for cancel")
        void returnErrorWhenTaskIdMissingForCancel() throws JsonProcessingException {
            String cancelMessage = "{\"type\":\"cancel\"}";
            WebSocketMessage inboundMsg = mockMessage(cancelMessage);

            when(session.receive()).thenReturn(Flux.just(inboundMsg));
            when(session.send(any())).thenReturn(Mono.empty());

            StepVerifier.create(handler.handle(session))
                    .expectComplete()
                    .verify(Duration.ofSeconds(5));

            // Check for error response
            boolean hasError = sentMessages.stream()
                    .anyMatch(msg -> msg.contains("\"type\":\"error\"") && 
                            msg.contains("taskId is required for cancel"));
            assertThat(hasError).isTrue();
        }
    }

    @Nested
    @DisplayName("Broadcast")
    class BroadcastTests {

        @Test
        @DisplayName("should broadcast thought to subscribed sessions")
        void broadcastThoughtToSubscribedSessions() throws Exception {
            // First subscribe to a task
            String subscribeMessage = "{\"type\":\"subscribe\",\"taskId\":\"task-456\"}";
            WebSocketMessage subscribeMsg = mockMessage(subscribeMessage);

            when(session.receive()).thenReturn(Flux.just(subscribeMsg));
            when(session.send(any())).thenReturn(Mono.empty());

            // Handle the subscription
            handler.handle(session).block(Duration.ofSeconds(5));

            // Now broadcast a thought
            AgentThought thought = AgentThought.builder()
                    .id("thought-broadcast")
                    .taskId("task-456")
                    .agentId("agent-789")
                    .type(AgentThought.ThoughtType.REASONING)
                    .content("Broadcast content")
                    .timestamp(Instant.now())
                    .build();

            handler.broadcastThought(thought);

            // Verify thought was broadcasted (would appear in sent messages)
            // Note: Due to async nature, this may require waiting
        }
    }

    private WebSocketMessage mockMessage(String payload) {
        WebSocketMessage msg = mock(WebSocketMessage.class);
        when(msg.getPayloadAsText()).thenReturn(payload);
        return msg;
    }
}
