package com.z254.butterfly.cortex.orchestration.pattern;

import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.orchestration.AgentMessageBus;
import com.z254.butterfly.cortex.orchestration.ResultAggregator;
import com.z254.butterfly.cortex.orchestration.TaskDecomposer;
import reactor.core.publisher.Flux;

/**
 * Interface for coordination pattern executors.
 * Each pattern implements a different strategy for multi-agent coordination.
 */
public interface CoordinationPatternExecutor {

    /**
     * Get the coordination pattern this executor implements.
     */
    CoordinationConfig.CoordinationPattern getPattern();

    /**
     * Execute a team task using this coordination pattern.
     *
     * @param task             The task to execute
     * @param team             The team executing the task
     * @param agentService     Service for executing individual agent tasks
     * @param taskDecomposer   Service for decomposing tasks
     * @param resultAggregator Service for aggregating results
     * @param messageBus       Bus for inter-agent communication
     * @return Stream of thoughts from the execution
     */
    Flux<TeamThought> execute(
            TeamTask task,
            AgentTeam team,
            AgentService agentService,
            TaskDecomposer taskDecomposer,
            ResultAggregator resultAggregator,
            AgentMessageBus messageBus);

    /**
     * Get the name of this pattern for logging/display.
     */
    default String getName() {
        return getPattern().name();
    }

    /**
     * Check if this pattern supports the given team configuration.
     */
    default boolean supportsTeam(AgentTeam team) {
        return true;
    }
}
