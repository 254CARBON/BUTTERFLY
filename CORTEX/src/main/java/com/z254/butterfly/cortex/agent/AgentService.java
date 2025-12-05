package com.z254.butterfly.cortex.agent;

import com.z254.butterfly.cortex.domain.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for agent operations.
 * Provides high-level operations for agent management and task execution.
 */
public interface AgentService {

    // ==================== Agent Management ====================

    /**
     * Create a new agent.
     *
     * @param agent the agent to create
     * @return the created agent
     */
    Mono<Agent> createAgent(Agent agent);

    /**
     * Get an agent by ID.
     *
     * @param agentId the agent ID
     * @return the agent or empty
     */
    Mono<Agent> getAgent(String agentId);

    /**
     * Update an existing agent.
     *
     * @param agentId the agent ID
     * @param agent the updated agent
     * @return the updated agent
     */
    Mono<Agent> updateAgent(String agentId, Agent agent);

    /**
     * Delete an agent.
     *
     * @param agentId the agent ID
     * @return completion signal
     */
    Mono<Void> deleteAgent(String agentId);

    /**
     * List all agents.
     *
     * @return all agents
     */
    Flux<Agent> listAgents();

    /**
     * List agents by namespace.
     *
     * @param namespace the namespace
     * @return agents in namespace
     */
    Flux<Agent> listAgentsByNamespace(String namespace);

    // ==================== Task Execution ====================

    /**
     * Submit a task for execution.
     *
     * @param task the task to execute
     * @return the submitted task with ID assigned
     */
    Mono<AgentTask> submitTask(AgentTask task);

    /**
     * Execute a task and stream thoughts in real-time.
     *
     * @param task the task to execute
     * @return flux of thoughts during execution
     */
    Flux<AgentThought> executeTaskStreaming(AgentTask task);

    /**
     * Execute a task synchronously.
     *
     * @param task the task to execute
     * @return the complete result
     */
    Mono<AgentResult> executeTask(AgentTask task);

    /**
     * Get task status.
     *
     * @param taskId the task ID
     * @return the task or empty
     */
    Mono<AgentTask> getTask(String taskId);

    /**
     * Get task result.
     *
     * @param taskId the task ID
     * @return the result or empty
     */
    Mono<AgentResult> getTaskResult(String taskId);

    /**
     * Cancel a running task.
     *
     * @param taskId the task ID
     * @param reason cancellation reason
     * @return the updated task
     */
    Mono<AgentTask> cancelTask(String taskId, String reason);

    // ==================== Conversation Management ====================

    /**
     * Create a new conversation.
     *
     * @param agentId the agent ID
     * @param userId the user ID
     * @return the created conversation
     */
    Mono<Conversation> createConversation(String agentId, String userId);

    /**
     * Get a conversation by ID.
     *
     * @param conversationId the conversation ID
     * @return the conversation or empty
     */
    Mono<Conversation> getConversation(String conversationId);

    /**
     * Send a message in a conversation.
     *
     * @param conversationId the conversation ID
     * @param message the user message
     * @return flux of thoughts during response generation
     */
    Flux<AgentThought> chat(String conversationId, String message);

    /**
     * Send a message and get the complete response.
     *
     * @param conversationId the conversation ID
     * @param message the user message
     * @return the complete result
     */
    Mono<AgentResult> chatSync(String conversationId, String message);

    /**
     * List conversations for a user.
     *
     * @param userId the user ID
     * @return conversations for the user
     */
    Flux<Conversation> listConversations(String userId);

    /**
     * Archive a conversation.
     *
     * @param conversationId the conversation ID
     * @return the updated conversation
     */
    Mono<Conversation> archiveConversation(String conversationId);

    /**
     * Delete a conversation permanently.
     *
     * @param conversationId the conversation ID
     * @return completion signal
     */
    Mono<Void> deleteConversation(String conversationId);
}
