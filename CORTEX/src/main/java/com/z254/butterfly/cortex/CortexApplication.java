package com.z254.butterfly.cortex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CORTEX - AI Agent Orchestration Layer for the BUTTERFLY Ecosystem.
 * 
 * <p>CORTEX provides:
 * <ul>
 *   <li>Agent Framework - ReAct, PlanAndExecute, and Reflexive agent implementations</li>
 *   <li>LLM Abstraction - Unified interface for OpenAI, Anthropic, Ollama providers</li>
 *   <li>Memory Systems - Episodic, semantic, and working memory with CAPSULE integration</li>
 *   <li>Tool Interface - Bridge to SYNAPSE tool execution with governance</li>
 *   <li>Streaming APIs - Real-time thought streaming via WebSocket and SSE</li>
 *   <li>Safety Controls - Token budgets, guardrails, and execution quotas</li>
 * </ul>
 * 
 * <p>CORTEX integrates with:
 * <ul>
 *   <li>SYNAPSE - For tool execution and action management</li>
 *   <li>PLATO - For governance, approval workflows, and policy enforcement</li>
 *   <li>CAPSULE - For long-term memory persistence and historical context</li>
 *   <li>PERCEPTION - For entity queries and event lookups</li>
 *   <li>ODYSSEY - For world state and strategic context</li>
 *   <li>NEXUS - For cross-system reasoning integration</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class CortexApplication {

    public static void main(String[] args) {
        SpringApplication.run(CortexApplication.class, args);
    }
}
