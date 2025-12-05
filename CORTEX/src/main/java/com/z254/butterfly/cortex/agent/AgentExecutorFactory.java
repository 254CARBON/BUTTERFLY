package com.z254.butterfly.cortex.agent;

import com.z254.butterfly.cortex.config.CortexProperties.AgentType;
import com.z254.butterfly.cortex.domain.model.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating and retrieving agent executors based on agent type.
 */
@Component
@Slf4j
public class AgentExecutorFactory {

    private final Map<AgentType, AgentExecutor> executors = new HashMap<>();

    public AgentExecutorFactory(List<AgentExecutor> executorList) {
        for (AgentExecutor executor : executorList) {
            executors.put(executor.getAgentType(), executor);
            log.info("Registered agent executor for type: {}", executor.getAgentType());
        }
    }

    /**
     * Get the executor for an agent type.
     *
     * @param type the agent type
     * @return the executor
     * @throws IllegalArgumentException if no executor is found
     */
    public AgentExecutor getExecutor(AgentType type) {
        AgentExecutor executor = executors.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("No executor found for agent type: " + type);
        }
        return executor;
    }

    /**
     * Get the executor for an agent.
     *
     * @param agent the agent
     * @return the executor
     */
    public AgentExecutor getExecutor(Agent agent) {
        return getExecutor(agent.getType());
    }

    /**
     * Check if an executor exists for the given type.
     *
     * @param type the agent type
     * @return true if an executor exists
     */
    public boolean hasExecutor(AgentType type) {
        return executors.containsKey(type);
    }

    /**
     * Get all registered agent types.
     *
     * @return set of agent types
     */
    public java.util.Set<AgentType> getRegisteredTypes() {
        return executors.keySet();
    }
}
