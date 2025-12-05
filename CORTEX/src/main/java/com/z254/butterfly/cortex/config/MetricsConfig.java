package com.z254.butterfly.cortex.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metrics configuration for CORTEX service.
 * Defines custom metrics for agent execution, LLM calls, and memory operations.
 */
@Configuration
public class MetricsConfig {

    // ==================== Agent Metrics ====================

    @Bean
    public Counter agentTasksSubmittedCounter(MeterRegistry registry) {
        return Counter.builder("cortex.agent.tasks.submitted")
                .description("Total agent tasks submitted")
                .register(registry);
    }

    @Bean
    public Counter agentTasksCompletedCounter(MeterRegistry registry) {
        return Counter.builder("cortex.agent.tasks.completed")
                .description("Total agent tasks completed successfully")
                .register(registry);
    }

    @Bean
    public Counter agentTasksFailedCounter(MeterRegistry registry) {
        return Counter.builder("cortex.agent.tasks.failed")
                .description("Total agent tasks failed")
                .register(registry);
    }

    @Bean
    public Timer agentTaskExecutionTimer(MeterRegistry registry) {
        return Timer.builder("cortex.agent.task.execution")
                .description("Agent task execution time")
                .register(registry);
    }

    @Bean
    public Counter agentThoughtsGeneratedCounter(MeterRegistry registry) {
        return Counter.builder("cortex.agent.thoughts.generated")
                .description("Total agent thoughts generated")
                .register(registry);
    }

    // ==================== LLM Metrics ====================

    @Bean
    public Counter llmCallsCounter(MeterRegistry registry) {
        return Counter.builder("cortex.llm.calls")
                .description("Total LLM API calls")
                .register(registry);
    }

    @Bean
    public Counter llmCallsFailedCounter(MeterRegistry registry) {
        return Counter.builder("cortex.llm.calls.failed")
                .description("Total failed LLM API calls")
                .register(registry);
    }

    @Bean
    public Timer llmCallLatencyTimer(MeterRegistry registry) {
        return Timer.builder("cortex.llm.call.latency")
                .description("LLM API call latency")
                .register(registry);
    }

    @Bean
    public Counter llmTokensInputCounter(MeterRegistry registry) {
        return Counter.builder("cortex.llm.tokens.input")
                .description("Total LLM input tokens")
                .register(registry);
    }

    @Bean
    public Counter llmTokensOutputCounter(MeterRegistry registry) {
        return Counter.builder("cortex.llm.tokens.output")
                .description("Total LLM output tokens")
                .register(registry);
    }

    // ==================== Memory Metrics ====================

    @Bean
    public Counter memoryStoreOperationsCounter(MeterRegistry registry) {
        return Counter.builder("cortex.memory.store.operations")
                .description("Total memory store operations")
                .register(registry);
    }

    @Bean
    public Counter memoryRetrieveOperationsCounter(MeterRegistry registry) {
        return Counter.builder("cortex.memory.retrieve.operations")
                .description("Total memory retrieve operations")
                .register(registry);
    }

    @Bean
    public Timer memoryOperationTimer(MeterRegistry registry) {
        return Timer.builder("cortex.memory.operation.latency")
                .description("Memory operation latency")
                .register(registry);
    }

    // ==================== Tool Metrics ====================

    @Bean
    public Counter toolCallsCounter(MeterRegistry registry) {
        return Counter.builder("cortex.tool.calls")
                .description("Total tool calls")
                .register(registry);
    }

    @Bean
    public Counter toolCallsFailedCounter(MeterRegistry registry) {
        return Counter.builder("cortex.tool.calls.failed")
                .description("Total failed tool calls")
                .register(registry);
    }

    @Bean
    public Timer toolCallLatencyTimer(MeterRegistry registry) {
        return Timer.builder("cortex.tool.call.latency")
                .description("Tool call latency")
                .register(registry);
    }

    // ==================== Governance Metrics ====================

    @Bean
    public Counter governanceChecksCounter(MeterRegistry registry) {
        return Counter.builder("cortex.governance.checks")
                .description("Total governance checks")
                .register(registry);
    }

    @Bean
    public Counter governanceChecksPassedCounter(MeterRegistry registry) {
        return Counter.builder("cortex.governance.checks.passed")
                .description("Total governance checks passed")
                .register(registry);
    }

    @Bean
    public Counter governanceChecksFailedCounter(MeterRegistry registry) {
        return Counter.builder("cortex.governance.checks.failed")
                .description("Total governance checks failed")
                .register(registry);
    }

    // ==================== Safety Metrics ====================

    @Bean
    public Counter safetyViolationsCounter(MeterRegistry registry) {
        return Counter.builder("cortex.safety.violations")
                .description("Total safety violations detected")
                .register(registry);
    }

    @Bean
    public Counter tokenBudgetExceededCounter(MeterRegistry registry) {
        return Counter.builder("cortex.safety.token_budget.exceeded")
                .description("Total times token budget exceeded")
                .register(registry);
    }
}
