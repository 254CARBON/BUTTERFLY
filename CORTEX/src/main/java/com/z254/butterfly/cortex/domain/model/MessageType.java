package com.z254.butterfly.cortex.domain.model;

/**
 * Types of messages exchanged between agents in multi-agent coordination.
 */
public enum MessageType {

    /**
     * Request for another agent to perform a task.
     */
    REQUEST,

    /**
     * Response to a request with results.
     */
    RESPONSE,

    /**
     * Critique or feedback on another agent's output.
     */
    CRITIQUE,

    /**
     * Request for the receiving agent to take over a task.
     */
    HANDOFF,

    /**
     * Suggested refinement or improvement to previous output.
     */
    REFINEMENT,

    /**
     * Information broadcast to all team members.
     */
    BROADCAST,

    /**
     * Query for information from another agent.
     */
    QUERY,

    /**
     * Acknowledgment of message receipt.
     */
    ACK,

    /**
     * Vote or position in a debate scenario.
     */
    VOTE,

    /**
     * Agreement with another agent's position.
     */
    AGREE,

    /**
     * Disagreement with another agent's position.
     */
    DISAGREE,

    /**
     * Proposal for consensus in debate.
     */
    PROPOSAL,

    /**
     * Status update on task progress.
     */
    STATUS_UPDATE,

    /**
     * Error notification.
     */
    ERROR,

    /**
     * Completion notification.
     */
    COMPLETION
}
