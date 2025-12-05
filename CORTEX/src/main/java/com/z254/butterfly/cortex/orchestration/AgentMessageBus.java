package com.z254.butterfly.cortex.orchestration;

import com.z254.butterfly.cortex.domain.model.AgentMessage;
import com.z254.butterfly.cortex.domain.model.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Message bus for inter-agent communication in multi-agent orchestration.
 * Provides reactive message passing between agents with support for:
 * - Point-to-point messaging
 * - Broadcast messaging
 * - Message threading
 * - Conversation tracking
 */
@Service
@Slf4j
public class AgentMessageBus {

    // Global message sink for all messages
    private final Sinks.Many<AgentMessage> globalSink;
    
    // Per-agent message sinks for subscriptions
    private final Map<String, Sinks.Many<AgentMessage>> agentSinks;
    
    // Per-task message sinks
    private final Map<String, Sinks.Many<AgentMessage>> taskSinks;
    
    // Message storage (for history/replay)
    private final Map<String, List<AgentMessage>> messagesByTask;
    private final Map<String, List<AgentMessage>> messagesByAgent;
    
    // Message deduplication
    private final Set<String> processedMessageIds;
    
    // Configuration
    private final int maxMessagesPerTask;
    private final Duration messageRetention;

    public AgentMessageBus() {
        this.globalSink = Sinks.many().multicast().onBackpressureBuffer();
        this.agentSinks = new ConcurrentHashMap<>();
        this.taskSinks = new ConcurrentHashMap<>();
        this.messagesByTask = new ConcurrentHashMap<>();
        this.messagesByAgent = new ConcurrentHashMap<>();
        this.processedMessageIds = ConcurrentHashMap.newKeySet();
        this.maxMessagesPerTask = 1000;
        this.messageRetention = Duration.ofHours(24);
        
        // Subscribe to global sink for message routing
        globalSink.asFlux()
                .subscribe(this::routeMessage);
        
        log.info("Initialized AgentMessageBus");
    }

    /**
     * Send a message.
     *
     * @param message The message to send
     * @return Mono that completes when message is sent
     */
    public Mono<Void> send(AgentMessage message) {
        if (message == null) {
            return Mono.empty();
        }
        
        // Validate and prepare message
        if (message.getId() == null) {
            message.setId(UUID.randomUUID().toString());
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(Instant.now());
        }
        
        // Check for duplicates
        if (!processedMessageIds.add(message.getId())) {
            log.debug("Duplicate message ignored: {}", message.getId());
            return Mono.empty();
        }
        
        // Check message limits
        String taskId = message.getTeamTaskId();
        if (taskId != null) {
            List<AgentMessage> taskMessages = messagesByTask.computeIfAbsent(taskId, 
                    k -> Collections.synchronizedList(new ArrayList<>()));
            if (taskMessages.size() >= maxMessagesPerTask) {
                log.warn("Message limit reached for task: {}", taskId);
                return Mono.error(new IllegalStateException("Message limit reached for task"));
            }
        }
        
        // Store message
        storeMessage(message);
        
        // Emit to global sink
        Sinks.EmitResult result = globalSink.tryEmitNext(message);
        if (result.isFailure()) {
            log.warn("Failed to emit message: {} - {}", message.getId(), result);
        }
        
        log.debug("Message sent: {} -> {} (type: {})", 
                message.getFromAgentId(), 
                message.getToAgentId() != null ? message.getToAgentId() : "broadcast",
                message.getType());
        
        return Mono.empty();
    }

    /**
     * Subscribe to messages for a specific agent.
     *
     * @param agentId The agent ID to subscribe for
     * @return Flux of messages addressed to this agent
     */
    public Flux<AgentMessage> subscribe(String agentId) {
        Sinks.Many<AgentMessage> sink = agentSinks.computeIfAbsent(agentId, 
                k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux()
                .doOnSubscribe(s -> log.debug("Agent {} subscribed to message bus", agentId))
                .doOnCancel(() -> log.debug("Agent {} unsubscribed from message bus", agentId));
    }

    /**
     * Subscribe to all messages for a team task.
     *
     * @param teamTaskId The team task ID
     * @return Flux of all messages in the task
     */
    public Flux<AgentMessage> subscribeToTask(String teamTaskId) {
        Sinks.Many<AgentMessage> sink = taskSinks.computeIfAbsent(teamTaskId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux();
    }

    /**
     * Get conversation history for a team task.
     *
     * @param teamTaskId The team task ID
     * @return Flux of messages in the conversation
     */
    public Flux<AgentMessage> getConversation(String teamTaskId) {
        List<AgentMessage> messages = messagesByTask.get(teamTaskId);
        if (messages == null) {
            return Flux.empty();
        }
        return Flux.fromIterable(new ArrayList<>(messages))
                .sort(Comparator.comparing(AgentMessage::getTimestamp));
    }

    /**
     * Get messages sent by a specific agent.
     *
     * @param agentId The agent ID
     * @return Flux of messages from the agent
     */
    public Flux<AgentMessage> getMessagesByAgent(String agentId) {
        List<AgentMessage> messages = messagesByAgent.get(agentId);
        if (messages == null) {
            return Flux.empty();
        }
        return Flux.fromIterable(new ArrayList<>(messages))
                .sort(Comparator.comparing(AgentMessage::getTimestamp));
    }

    /**
     * Get message thread starting from a specific message.
     *
     * @param messageId The root message ID
     * @return Flux of messages in the thread
     */
    public Flux<AgentMessage> getThread(String messageId) {
        // Find the root message first
        AgentMessage root = findMessage(messageId);
        if (root == null) {
            return Flux.empty();
        }
        
        String taskId = root.getTeamTaskId();
        if (taskId == null) {
            return Flux.just(root);
        }
        
        // Find all messages in the thread
        List<AgentMessage> taskMessages = messagesByTask.get(taskId);
        if (taskMessages == null) {
            return Flux.just(root);
        }
        
        Set<String> threadIds = new HashSet<>();
        threadIds.add(messageId);
        
        // Find all replies
        boolean found = true;
        while (found) {
            found = false;
            for (AgentMessage msg : taskMessages) {
                if (msg.getParentMessageId() != null && 
                    threadIds.contains(msg.getParentMessageId()) &&
                    !threadIds.contains(msg.getId())) {
                    threadIds.add(msg.getId());
                    found = true;
                }
            }
        }
        
        return Flux.fromIterable(taskMessages)
                .filter(msg -> threadIds.contains(msg.getId()))
                .sort(Comparator.comparing(AgentMessage::getTimestamp));
    }

    /**
     * Broadcast a message to all agents in a task.
     *
     * @param teamTaskId  The team task ID
     * @param fromAgentId The sending agent ID
     * @param content     The message content
     * @return Mono that completes when broadcast is sent
     */
    public Mono<Void> broadcast(String teamTaskId, String fromAgentId, String content) {
        AgentMessage message = AgentMessage.broadcast(teamTaskId, fromAgentId, content);
        return send(message);
    }

    /**
     * Send a request message and wait for response.
     *
     * @param message The request message
     * @param timeout Timeout for waiting for response
     * @return Mono with the response message, or empty on timeout
     */
    public Mono<AgentMessage> sendAndWaitForResponse(AgentMessage message, Duration timeout) {
        if (message.getType() != MessageType.REQUEST) {
            message.setType(MessageType.REQUEST);
        }
        message.setRequiresAck(true);
        
        String messageId = message.getId() != null ? message.getId() : UUID.randomUUID().toString();
        message.setId(messageId);
        
        // Subscribe to responses before sending
        Flux<AgentMessage> responses = subscribeToTask(message.getTeamTaskId())
                .filter(m -> messageId.equals(m.getParentMessageId()))
                .filter(m -> m.getType() == MessageType.RESPONSE);
        
        return send(message)
                .then(responses.next().timeout(timeout).onErrorResume(e -> Mono.empty()));
    }

    /**
     * Acknowledge a message.
     *
     * @param originalMessage The message to acknowledge
     * @param agentId         The acknowledging agent
     * @return Mono that completes when ack is sent
     */
    public Mono<Void> acknowledge(AgentMessage originalMessage, String agentId) {
        AgentMessage ack = AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(originalMessage.getTeamTaskId())
                .fromAgentId(agentId)
                .toAgentId(originalMessage.getFromAgentId())
                .parentMessageId(originalMessage.getId())
                .type(MessageType.ACK)
                .content("Acknowledged")
                .timestamp(Instant.now())
                .build();
        
        originalMessage.acknowledge();
        return send(ack);
    }

    /**
     * Clean up resources for a completed task.
     *
     * @param teamTaskId The team task ID
     */
    public void cleanupTask(String teamTaskId) {
        Sinks.Many<AgentMessage> sink = taskSinks.remove(teamTaskId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        
        // Remove old messages
        List<AgentMessage> messages = messagesByTask.remove(teamTaskId);
        if (messages != null) {
            for (AgentMessage msg : messages) {
                processedMessageIds.remove(msg.getId());
            }
        }
        
        log.debug("Cleaned up message bus for task: {}", teamTaskId);
    }

    /**
     * Get statistics about the message bus.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessages", processedMessageIds.size());
        stats.put("activeTasks", taskSinks.size());
        stats.put("subscribedAgents", agentSinks.size());
        
        int totalTaskMessages = messagesByTask.values().stream()
                .mapToInt(List::size)
                .sum();
        stats.put("storedMessages", totalTaskMessages);
        
        return stats;
    }

    // --------------------------------------------------------------------------------------------
    // Internal helpers
    // --------------------------------------------------------------------------------------------

    private void routeMessage(AgentMessage message) {
        // Route to specific agent if addressed
        if (message.getToAgentId() != null && !message.isBroadcast()) {
            Sinks.Many<AgentMessage> agentSink = agentSinks.get(message.getToAgentId());
            if (agentSink != null) {
                agentSink.tryEmitNext(message);
            }
        } else {
            // Broadcast to all agents in the task
            String taskId = message.getTeamTaskId();
            if (taskId != null) {
                for (Map.Entry<String, Sinks.Many<AgentMessage>> entry : agentSinks.entrySet()) {
                    if (!entry.getKey().equals(message.getFromAgentId())) {
                        entry.getValue().tryEmitNext(message);
                    }
                }
            }
        }
        
        // Also emit to task sink
        if (message.getTeamTaskId() != null) {
            Sinks.Many<AgentMessage> taskSink = taskSinks.get(message.getTeamTaskId());
            if (taskSink != null) {
                taskSink.tryEmitNext(message);
            }
        }
    }

    private void storeMessage(AgentMessage message) {
        // Store by task
        if (message.getTeamTaskId() != null) {
            messagesByTask.computeIfAbsent(message.getTeamTaskId(),
                    k -> Collections.synchronizedList(new ArrayList<>())).add(message);
        }
        
        // Store by sender
        if (message.getFromAgentId() != null) {
            messagesByAgent.computeIfAbsent(message.getFromAgentId(),
                    k -> Collections.synchronizedList(new ArrayList<>())).add(message);
        }
    }

    private AgentMessage findMessage(String messageId) {
        for (List<AgentMessage> messages : messagesByTask.values()) {
            for (AgentMessage msg : messages) {
                if (messageId.equals(msg.getId())) {
                    return msg;
                }
            }
        }
        return null;
    }
}
