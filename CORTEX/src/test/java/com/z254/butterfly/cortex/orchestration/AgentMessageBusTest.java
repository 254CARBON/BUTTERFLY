package com.z254.butterfly.cortex.orchestration;

import com.z254.butterfly.cortex.domain.model.AgentMessage;
import com.z254.butterfly.cortex.domain.model.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AgentMessageBus.
 */
class AgentMessageBusTest {

    private AgentMessageBus messageBus;

    @BeforeEach
    void setUp() {
        messageBus = new AgentMessageBus();
    }

    @Test
    void shouldSendAndReceiveMessages() {
        // Given
        String taskId = UUID.randomUUID().toString();
        String fromAgent = "agent1";
        String toAgent = "agent2";
        
        // Subscribe first
        Flux<AgentMessage> subscription = messageBus.subscribe(toAgent);

        // When
        AgentMessage message = AgentMessage.request(taskId, fromAgent, toAgent, 
                "Hello", Map.of("key", "value"));
        messageBus.send(message).subscribe();

        // Then
        StepVerifier.create(subscription.take(1))
                .assertNext(received -> {
                    assertThat(received.getContent()).isEqualTo("Hello");
                    assertThat(received.getFromAgentId()).isEqualTo(fromAgent);
                    assertThat(received.getToAgentId()).isEqualTo(toAgent);
                })
                .verifyComplete();
    }

    @Test
    void shouldBroadcastMessages() {
        // Given
        String taskId = UUID.randomUUID().toString();
        String fromAgent = "agent1";
        
        // Subscribe two agents
        Flux<AgentMessage> sub1 = messageBus.subscribe("agent2");
        Flux<AgentMessage> sub2 = messageBus.subscribe("agent3");

        // When
        messageBus.broadcast(taskId, fromAgent, "Broadcast message").subscribe();

        // Then
        StepVerifier.create(sub1.take(1))
                .assertNext(received -> {
                    assertThat(received.getContent()).isEqualTo("Broadcast message");
                    assertThat(received.getType()).isEqualTo(MessageType.BROADCAST);
                })
                .verifyComplete();
        
        StepVerifier.create(sub2.take(1))
                .assertNext(received -> {
                    assertThat(received.getContent()).isEqualTo("Broadcast message");
                })
                .verifyComplete();
    }

    @Test
    void shouldTrackConversationHistory() {
        // Given
        String taskId = UUID.randomUUID().toString();
        
        // Send multiple messages
        messageBus.send(AgentMessage.request(taskId, "agent1", "agent2", "Message 1", null)).block();
        messageBus.send(AgentMessage.response(taskId, "agent2", "agent1", null, "Reply 1", null)).block();
        messageBus.send(AgentMessage.request(taskId, "agent1", "agent2", "Message 2", null)).block();

        // When
        Flux<AgentMessage> conversation = messageBus.getConversation(taskId);

        // Then
        StepVerifier.create(conversation)
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    void shouldDeduplicateMessages() {
        // Given
        String taskId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();
        
        AgentMessage message = AgentMessage.builder()
                .id(messageId)
                .teamTaskId(taskId)
                .fromAgentId("agent1")
                .toAgentId("agent2")
                .type(MessageType.REQUEST)
                .content("Test")
                .build();

        // When - send same message twice
        messageBus.send(message).block();
        messageBus.send(message).block(); // Duplicate

        // Then - should only have one message
        Flux<AgentMessage> conversation = messageBus.getConversation(taskId);
        StepVerifier.create(conversation)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldCleanupTask() {
        // Given
        String taskId = UUID.randomUUID().toString();
        
        messageBus.send(AgentMessage.request(taskId, "agent1", "agent2", "Test", null)).block();

        // When
        messageBus.cleanupTask(taskId);

        // Then
        Flux<AgentMessage> conversation = messageBus.getConversation(taskId);
        StepVerifier.create(conversation)
                .verifyComplete(); // Empty
    }

    @Test
    void shouldProvideStats() {
        // Given
        String taskId = UUID.randomUUID().toString();
        messageBus.send(AgentMessage.request(taskId, "agent1", "agent2", "Test", null)).block();

        // When
        Map<String, Object> stats = messageBus.getStats();

        // Then
        assertThat(stats).containsKey("totalMessages");
        assertThat(stats.get("totalMessages")).isEqualTo(1);
    }
}
