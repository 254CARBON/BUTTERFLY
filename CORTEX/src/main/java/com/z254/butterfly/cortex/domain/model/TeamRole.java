package com.z254.butterfly.cortex.domain.model;

/**
 * Roles that agents can play within a team.
 * Each role defines the agent's responsibility and behavior in multi-agent coordination.
 */
public enum TeamRole {

    /**
     * The orchestrator agent is responsible for:
     * - Decomposing complex tasks into subtasks
     * - Assigning subtasks to appropriate specialists
     * - Coordinating execution flow
     * - Synthesizing final results
     */
    ORCHESTRATOR,

    /**
     * Specialist agents have deep expertise in specific domains.
     * They execute subtasks that match their specialization.
     */
    SPECIALIST,

    /**
     * Critic agents review outputs from other agents.
     * They validate quality, identify issues, and suggest improvements.
     */
    CRITIC,

    /**
     * Synthesizer agents combine outputs from multiple agents
     * into a coherent, unified result.
     */
    SYNTHESIZER,

    /**
     * Researcher agents gather information and evidence
     * to support decision-making by other agents.
     */
    RESEARCHER,

    /**
     * Validator agents verify facts, check consistency,
     * and ensure outputs meet requirements.
     */
    VALIDATOR,

    /**
     * General-purpose worker agent that can handle
     * a variety of tasks without specific specialization.
     */
    WORKER
}
