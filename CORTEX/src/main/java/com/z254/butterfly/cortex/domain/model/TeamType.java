package com.z254.butterfly.cortex.domain.model;

/**
 * Types of agent teams that define coordination patterns.
 */
public enum TeamType {

    /**
     * Hierarchical team with a clear orchestrator-worker structure.
     * The orchestrator decomposes tasks and delegates to specialists.
     */
    HIERARCHICAL,

    /**
     * Collaborative team where agents work together as peers.
     * Tasks are distributed based on capability matching.
     */
    COLLABORATIVE,

    /**
     * Debate-style team where agents argue different positions.
     * A consensus is reached through structured argumentation.
     */
    DEBATE,

    /**
     * Chain-of-thought team where agents process sequentially.
     * Each agent refines the output of the previous one.
     */
    CHAIN,

    /**
     * Parallel team where agents work independently.
     * Results are aggregated at the end.
     */
    PARALLEL,

    /**
     * Swarm team with emergent coordination.
     * Agents self-organize based on task requirements.
     */
    SWARM
}
