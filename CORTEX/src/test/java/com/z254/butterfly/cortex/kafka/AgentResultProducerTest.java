package com.z254.butterfly.cortex.kafka;

import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.AgentResult;
import com.z254.butterfly.cortex.domain.model.AgentThought;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentResultProducer}.
 */
@ExtendWith(MockitoExtension.class)
class AgentResultProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<Map<String, Object>> messageCaptor;

    private CortexProperties cortexProperties;
    private MeterRegistry meterRegistry;
    private AgentResultProducer producer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cortexProperties = new CortexProperties();
        
        producer = new AgentResultProducer(kafkaTemplate, cortexProperties, meterRegistry);
    }

    @Nested
    @DisplayName("Result Publishing")
    class ResultPublishingTests {

        @Test
        @DisplayName("should publish result to configured topic")
        void publishResultToTopic() {
            String taskId = "task-123";
            AgentResult result = AgentResult.builder()
                    .taskId(taskId)
                    .agentId("agent-456")
                    .conversationId("conv-789")
                    .correlationId("corr-abc")
                    .output("Task completed successfully")
                    .status(AgentResult.ResultStatus.SUCCESS)
                    .iterations(3)
                    .startedAt(Instant.now().minusSeconds(10))
                    .completedAt(Instant.now())
                    .duration(Duration.ofSeconds(10))
                    .build();

            when(kafkaTemplate.send(any(String.class), eq(taskId), any()))
                    .thenReturn(createSuccessfulFuture());

            producer.publishResult(result);

            verify(kafkaTemplate).send(
                    eq("cortex.agent.results"),
                    eq(taskId),
                    messageCaptor.capture()
            );

            Map<String, Object> message = messageCaptor.getValue();
            assertThat(message.get("taskId")).isEqualTo(taskId);
            assertThat(message.get("agentId")).isEqualTo("agent-456");
            assertThat(message.get("conversationId")).isEqualTo("conv-789");
            assertThat(message.get("correlationId")).isEqualTo("corr-abc");
            assertThat(message.get("output")).isEqualTo("Task completed successfully");
            assertThat(message.get("status")).isEqualTo("SUCCESS");
            assertThat(message.get("iterations")).isEqualTo(3);
        }

        @Test
        @DisplayName("should include token usage in message")
        void includeTokenUsageInMessage() {
            String taskId = "task-123";
            AgentResult result = AgentResult.builder()
                    .taskId(taskId)
                    .agentId("agent-456")
                    .status(AgentResult.ResultStatus.SUCCESS)
                    .tokenUsage(AgentResult.TokenUsage.builder()
                            .inputTokens(500)
                            .outputTokens(300)
                            .totalTokens(800)
                            .build())
                    .duration(Duration.ofSeconds(5))
                    .build();

            when(kafkaTemplate.send(any(String.class), eq(taskId), any()))
                    .thenReturn(createSuccessfulFuture());

            producer.publishResult(result);

            verify(kafkaTemplate).send(any(String.class), eq(taskId), messageCaptor.capture());

            Map<String, Object> message = messageCaptor.getValue();
            assertThat(message.get("inputTokens")).isEqualTo(500);
            assertThat(message.get("outputTokens")).isEqualTo(300);
        }

        @Test
        @DisplayName("should handle null token usage gracefully")
        void handleNullTokenUsage() {
            String taskId = "task-123";
            AgentResult result = AgentResult.builder()
                    .taskId(taskId)
                    .agentId("agent-456")
                    .status(AgentResult.ResultStatus.SUCCESS)
                    .tokenUsage(null)
                    .build();

            when(kafkaTemplate.send(any(String.class), eq(taskId), any()))
                    .thenReturn(createSuccessfulFuture());

            producer.publishResult(result);

            verify(kafkaTemplate).send(any(String.class), eq(taskId), messageCaptor.capture());

            Map<String, Object> message = messageCaptor.getValue();
            assertThat(message).doesNotContainKey("inputTokens");
            assertThat(message).doesNotContainKey("outputTokens");
        }

        @Test
        @DisplayName("should handle failed result status")
        void handleFailedResultStatus() {
            String taskId = "task-123";
            AgentResult result = AgentResult.builder()
                    .taskId(taskId)
                    .agentId("agent-456")
                    .status(AgentResult.ResultStatus.FAILURE)
                    .errorCode("TOOL_TIMEOUT")
                    .errorMessage("Tool execution timed out after 30 seconds")
                    .build();

            when(kafkaTemplate.send(any(String.class), eq(taskId), any()))
                    .thenReturn(createSuccessfulFuture());

            producer.publishResult(result);

            verify(kafkaTemplate).send(any(String.class), eq(taskId), messageCaptor.capture());

            Map<String, Object> message = messageCaptor.getValue();
            assertThat(message.get("status")).isEqualTo("FAILURE");
            assertThat(message.get("errorCode")).isEqualTo("TOOL_TIMEOUT");
            assertThat(message.get("errorMessage")).isEqualTo("Tool execution timed out after 30 seconds");
        }

        @Test
        @DisplayName("should include duration in milliseconds")
        void includeDurationInMilliseconds() {
            String taskId = "task-123";
            AgentResult result = AgentResult.builder()
                    .taskId(taskId)
                    .agentId("agent-456")
                    .status(AgentResult.ResultStatus.SUCCESS)
                    .duration(Duration.ofMillis(5432))
                    .build();

            when(kafkaTemplate.send(any(String.class), eq(taskId), any()))
                    .thenReturn(createSuccessfulFuture());

            producer.publishResult(result);

            verify(kafkaTemplate).send(any(String.class), eq(taskId), messageCaptor.capture());

            Map<String, Object> message = messageCaptor.getValue();
            assertThat(message.get("durationMs")).isEqualTo(5432L);
        }
    }

    @Nested
    @DisplayName("Thought Publishing")
    class ThoughtPublishingTests {

        @Test
        @DisplayName("should publish thought to configured topic")
        void publishThoughtToTopic() {
            String taskId = "task-123";
            AgentThought thought = AgentThought.builder()
                    .id("thought-001")
                    .taskId(taskId)
                    .agentId("agent-456")
                    .type(AgentThought.ThoughtType.REASONING)
                    .content("Analyzing the user request...")
                    .iteration(1)
                    .tokenCount(45)
                    .timestamp(Instant.now())
                    .build();

            when(kafkaTemplate.send(any(String.class), eq(taskId), any()))
                    .thenReturn(createSuccessfulFuture());

            producer.publishThought(thought);

            verify(kafkaTemplate).send(
                    eq("cortex.agent.thoughts"),
                    eq(taskId),
                    messageCaptor.capture()
            );

            Map<String, Object> message = messageCaptor.getValue();
            assertThat(message.get("id")).isEqualTo("thought-001");
            assertThat(message.get("taskId")).isEqualTo(taskId);
            assertThat(message.get("agentId")).isEqualTo("agent-456");
            assertThat(message.get("type")).isEqualTo("REASONING");
            assertThat(message.get("content")).isEqualTo("Analyzing the user request...");
            assertThat(message.get("iteration")).isEqualTo(1);
            assertThat(message.get("tokenCount")).isEqualTo(45);
        }

        @Test
        @DisplayName("should include tool call information")
        void includeToolCallInformation() {
            String taskId = "task-123";
            AgentThought thought = AgentThought.builder()
                    .id("thought-002")
                    .taskId(taskId)
                    .agentId("agent-456")
                    .type(AgentThought.ThoughtType.TOOL_CALL)
                    .toolId("perception-query")
                    .toolParameters(Map.of("query", "SELECT * FROM events"))
                    .toolResult("Found 42 events")
                    .iteration(2)
                    .timestamp(Instant.now())
                    .build();

            when(kafkaTemplate.send(any(String.class), eq(taskId), any()))
                    .thenReturn(createSuccessfulFuture());

            producer.publishThought(thought);

            verify(kafkaTemplate).send(any(String.class), eq(taskId), messageCaptor.capture());

            Map<String, Object> message = messageCaptor.getValue();
            assertThat(message.get("type")).isEqualTo("TOOL_CALL");
            assertThat(message.get("toolId")).isEqualTo("perception-query");
            assertThat(message.get("toolResult")).isEqualTo("Found 42 events");
        }

        @Test
        @DisplayName("should handle final answer thought type")
        void handleFinalAnswerThoughtType() {
            String taskId = "task-123";
            AgentThought thought = AgentThought.builder()
                    .id("thought-final")
                    .taskId(taskId)
                    .agentId("agent-456")
                    .type(AgentThought.ThoughtType.FINAL_ANSWER)
                    .content("Based on my analysis, the answer is 42.")
                    .iteration(5)
                    .timestamp(Instant.now())
                    .build();

            when(kafkaTemplate.send(any(String.class), eq(taskId), any()))
                    .thenReturn(createSuccessfulFuture());

            producer.publishThought(thought);

            verify(kafkaTemplate).send(any(String.class), eq(taskId), messageCaptor.capture());

            Map<String, Object> message = messageCaptor.getValue();
            assertThat(message.get("type")).isEqualTo("FINAL_ANSWER");
        }
    }

    @Nested
    @DisplayName("Metrics")
    class MetricsTests {

        @Test
        @DisplayName("should increment result counter on successful publish")
        void incrementResultCounterOnSuccess() {
            String taskId = "task-123";
            AgentResult result = AgentResult.builder()
                    .taskId(taskId)
                    .agentId("agent-456")
                    .status(AgentResult.ResultStatus.SUCCESS)
                    .build();

            CompletableFuture<SendResult<String, Object>> future = createSuccessfulFuture();
            when(kafkaTemplate.send(any(String.class), eq(taskId), any()))
                    .thenReturn(future);

            producer.publishResult(result);

            // Wait for async completion
            future.join();

            Counter counter = meterRegistry.find("cortex.kafka.results.produced").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should increment thought counter on successful publish")
        void incrementThoughtCounterOnSuccess() {
            String taskId = "task-123";
            AgentThought thought = AgentThought.builder()
                    .id("thought-001")
                    .taskId(taskId)
                    .agentId("agent-456")
                    .type(AgentThought.ThoughtType.REASONING)
                    .timestamp(Instant.now())
                    .build();

            CompletableFuture<SendResult<String, Object>> future = createSuccessfulFuture();
            when(kafkaTemplate.send(any(String.class), eq(taskId), any()))
                    .thenReturn(future);

            producer.publishThought(thought);

            // Wait for async completion
            future.join();

            Counter counter = meterRegistry.find("cortex.kafka.thoughts.produced").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    private CompletableFuture<SendResult<String, Object>> createSuccessfulFuture() {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("test-topic", 0),
                0L, 0, 0L, 0, 0
        );
        ProducerRecord<String, Object> record = new ProducerRecord<>("test-topic", "key", "value");
        SendResult<String, Object> sendResult = new SendResult<>(record, metadata);
        return CompletableFuture.completedFuture(sendResult);
    }
}
