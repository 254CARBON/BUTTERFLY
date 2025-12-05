package com.z254.butterfly.cortex.agent;

import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.domain.model.AgentResult;
import com.z254.butterfly.cortex.domain.model.AgentTask;
import com.z254.butterfly.cortex.domain.model.AgentThought;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Interface for agent executors.
 * Each agent type (ReAct, PlanAndExecute, Reflexive) implements this interface.
 */
public interface AgentExecutor {

    /**
     * Execute a task asynchronously, streaming thoughts as they are generated.
     * This is the primary execution method for real-time UI updates.
     *
     * @param task the task to execute
     * @param context the execution context
     * @return flux of thoughts generated during execution
     */
    Flux<AgentThought> execute(AgentTask task, AgentContext context);

    /**
     * Execute a task synchronously, returning the complete result.
     * Use this when streaming is not needed.
     *
     * @param task the task to execute
     * @param context the execution context
     * @return the complete result
     */
    Mono<AgentResult> executeSync(AgentTask task, AgentContext context);

    /**
     * Get the agent type this executor handles.
     *
     * @return the agent type
     */
    com.z254.butterfly.cortex.config.CortexProperties.AgentType getAgentType();

    /**
     * Check if this executor can handle the given task.
     *
     * @param task the task to check
     * @return true if this executor can handle the task
     */
    default boolean canHandle(AgentTask task) {
        return true;
    }

    /**
     * Cancel an in-progress execution.
     *
     * @param taskId the task ID to cancel
     * @return true if cancellation was successful
     */
    default Mono<Boolean> cancel(String taskId) {
        return Mono.just(false);
    }

    /**
     * Get the current status of a running task.
     *
     * @param taskId the task ID
     * @return the current iteration/step or -1 if not running
     */
    default Mono<Integer> getCurrentIteration(String taskId) {
        return Mono.just(-1);
    }
}
