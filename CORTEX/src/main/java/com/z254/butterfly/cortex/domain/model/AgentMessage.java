package com.z254.butterfly.cortex.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a message exchanged between agents in multi-agent coordination.
 * Messages enable inter-agent communication, debate, and coordination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {

    /**
     * Unique identifier for this message.
     */
    private String id;

    /**
     * ID of the team task this message belongs to.
     */
    private String teamTaskId;

    /**
     * ID of the subtask this message relates to (if any).
     */
    private String subTaskId;

    /**
     * ID of the agent sending this message.
     */
    private String fromAgentId;

    /**
     * ID of the agent receiving this message.
     * Null for broadcast messages.
     */
    private String toAgentId;

    /**
     * Type of message.
     */
    private MessageType type;

    /**
     * Text content of the message.
     */
    private String content;

    /**
     * Structured payload for typed messages.
     */
    private Map<String, Object> payload;

    /**
     * ID of the parent message (for threading).
     */
    private String parentMessageId;

    /**
     * Sequence number in the conversation.
     */
    private int sequenceNumber;

    /**
     * Round number (for debate scenarios).
     */
    private int round;

    /**
     * Confidence level of the message content (0.0 - 1.0).
     */
    @Builder.Default
    private double confidence = 1.0;

    /**
     * Priority of this message.
     */
    @Builder.Default
    private int priority = 0;

    /**
     * Whether this message requires acknowledgment.
     */
    @Builder.Default
    private boolean requiresAck = false;

    /**
     * Whether this message has been acknowledged.
     */
    @Builder.Default
    private boolean acknowledged = false;

    /**
     * When this message was created.
     */
    private Instant timestamp;

    /**
     * When this message was delivered.
     */
    private Instant deliveredAt;

    /**
     * When this message was read/processed.
     */
    private Instant readAt;

    /**
     * Correlation ID for distributed tracing.
     */
    private String correlationId;

    /**
     * Check if this is a broadcast message.
     */
    public boolean isBroadcast() {
        return toAgentId == null || type == MessageType.BROADCAST;
    }

    /**
     * Check if this is a reply to another message.
     */
    public boolean isReply() {
        return parentMessageId != null;
    }

    /**
     * Mark this message as acknowledged.
     */
    public void acknowledge() {
        this.acknowledged = true;
    }

    /**
     * Mark this message as delivered.
     */
    public void markDelivered() {
        this.deliveredAt = Instant.now();
    }

    /**
     * Mark this message as read.
     */
    public void markRead() {
        this.readAt = Instant.now();
    }

    // Factory methods for common message types

    /**
     * Create a request message.
     */
    public static AgentMessage request(String teamTaskId, String fromAgentId, String toAgentId,
                                        String content, Map<String, Object> payload) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .fromAgentId(fromAgentId)
                .toAgentId(toAgentId)
                .type(MessageType.REQUEST)
                .content(content)
                .payload(payload)
                .requiresAck(true)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a response message.
     */
    public static AgentMessage response(String teamTaskId, String fromAgentId, String toAgentId,
                                         String parentMessageId, String content, Map<String, Object> payload) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .fromAgentId(fromAgentId)
                .toAgentId(toAgentId)
                .parentMessageId(parentMessageId)
                .type(MessageType.RESPONSE)
                .content(content)
                .payload(payload)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a critique message.
     */
    public static AgentMessage critique(String teamTaskId, String fromAgentId, String toAgentId,
                                         String subTaskId, String content, double confidence) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .subTaskId(subTaskId)
                .fromAgentId(fromAgentId)
                .toAgentId(toAgentId)
                .type(MessageType.CRITIQUE)
                .content(content)
                .confidence(confidence)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a handoff message.
     */
    public static AgentMessage handoff(String teamTaskId, String fromAgentId, String toAgentId,
                                        String subTaskId, String content, Map<String, Object> context) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .subTaskId(subTaskId)
                .fromAgentId(fromAgentId)
                .toAgentId(toAgentId)
                .type(MessageType.HANDOFF)
                .content(content)
                .payload(context)
                .requiresAck(true)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a broadcast message.
     */
    public static AgentMessage broadcast(String teamTaskId, String fromAgentId, String content) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .fromAgentId(fromAgentId)
                .type(MessageType.BROADCAST)
                .content(content)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a vote message for debate.
     */
    public static AgentMessage vote(String teamTaskId, String fromAgentId, String position,
                                     int round, double confidence) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .fromAgentId(fromAgentId)
                .type(MessageType.VOTE)
                .content(position)
                .round(round)
                .confidence(confidence)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a proposal message for consensus.
     */
    public static AgentMessage proposal(String teamTaskId, String fromAgentId, String content,
                                         int round, double confidence) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .fromAgentId(fromAgentId)
                .type(MessageType.PROPOSAL)
                .content(content)
                .round(round)
                .confidence(confidence)
                .requiresAck(true)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error message.
     */
    public static AgentMessage error(String teamTaskId, String fromAgentId, String toAgentId,
                                      String errorMessage) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .fromAgentId(fromAgentId)
                .toAgentId(toAgentId)
                .type(MessageType.ERROR)
                .content(errorMessage)
                .priority(10) // High priority
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a completion message.
     */
    public static AgentMessage completion(String teamTaskId, String fromAgentId, String subTaskId,
                                           String result) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(teamTaskId)
                .subTaskId(subTaskId)
                .fromAgentId(fromAgentId)
                .type(MessageType.COMPLETION)
                .content(result)
                .timestamp(Instant.now())
                .build();
    }
}
