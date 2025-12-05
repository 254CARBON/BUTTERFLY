package com.z254.butterfly.cortex.orchestration;

import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.domain.repository.AgentRepository;
import com.z254.butterfly.cortex.llm.LLMProvider;
import com.z254.butterfly.cortex.llm.LLMProviderRegistry;
import com.z254.butterfly.cortex.llm.LLMResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TaskDecomposer.
 */
@ExtendWith(MockitoExtension.class)
class TaskDecomposerTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private LLMProviderRegistry llmProviderRegistry;

    @Mock
    private LLMProvider llmProvider;

    private TaskDecomposer taskDecomposer;

    @BeforeEach
    void setUp() {
        taskDecomposer = new TaskDecomposer(agentRepository, llmProviderRegistry);
    }

    @Test
    void shouldDecomposeTaskIntoSubtasks() {
        // Given
        String taskId = UUID.randomUUID().toString();
        String teamId = UUID.randomUUID().toString();
        String orchestratorId = UUID.randomUUID().toString();
        
        TeamTask task = TeamTask.builder()
                .id(taskId)
                .teamId(teamId)
                .input("Build a web application with user authentication")
                .build();
        
        AgentTeam team = AgentTeam.builder()
                .id(teamId)
                .orchestratorAgentId(orchestratorId)
                .members(new ArrayList<>(List.of(
                        TeamMember.orchestrator(orchestratorId),
                        TeamMember.specialist("agent1", Set.of("backend")),
                        TeamMember.specialist("agent2", Set.of("frontend"))
                )))
                .build();
        
        Agent orchestratorAgent = Agent.builder()
                .id(orchestratorId)
                .llmConfig(Agent.LLMConfig.builder().providerId("openai").build())
                .build();
        
        String llmResponse = """
                SUBTASK 1:
                Description: Set up project structure and dependencies
                Capabilities: backend, configuration
                Domains: software
                Dependencies: none
                Complexity: low
                
                SUBTASK 2:
                Description: Implement user authentication system
                Capabilities: backend, security
                Domains: software
                Dependencies: 1
                Complexity: medium
                
                SUBTASK 3:
                Description: Build frontend UI components
                Capabilities: frontend, design
                Domains: software
                Dependencies: 1
                Complexity: medium
                """;
        
        when(agentRepository.findById(orchestratorId)).thenReturn(Mono.just(orchestratorAgent));
        when(llmProviderRegistry.getProvider("openai")).thenReturn(llmProvider);
        when(llmProvider.generate(any())).thenReturn(Mono.just(
                LLMResponse.builder().content(llmResponse).build()));

        // When
        Mono<List<SubTask>> result = taskDecomposer.decompose(task, team);

        // Then
        StepVerifier.create(result)
                .assertNext(subtasks -> {
                    assertThat(subtasks).hasSize(3);
                    assertThat(subtasks.get(0).getDescription()).contains("project structure");
                    assertThat(subtasks.get(1).getDescription()).contains("authentication");
                    assertThat(subtasks.get(2).getDescription()).contains("frontend");
                })
                .verifyComplete();
    }

    @Test
    void shouldSelectBestAgentForSubtask() {
        // Given
        SubTask subTask = SubTask.builder()
                .id("subtask1")
                .description("Implement authentication")
                .requiredCapabilities(Set.of("backend", "security"))
                .requiredDomains(Set.of("software"))
                .build();
        
        Agent generalAgent = Agent.builder()
                .id("agent1")
                .status(Agent.AgentStatus.ACTIVE)
                .specialization(AgentSpecialization.generalPurpose())
                .build();
        
        Agent securityAgent = Agent.builder()
                .id("agent2")
                .status(Agent.AgentStatus.ACTIVE)
                .specialization(AgentSpecialization.builder()
                        .domains(Set.of("software", "security"))
                        .capabilities(Set.of("backend", "security", "authentication"))
                        .domainScores(Map.of("software", 0.9, "security", 0.85))
                        .capabilityScores(Map.of("backend", 0.8, "security", 0.9))
                        .build())
                .build();
        
        List<Agent> candidates = List.of(generalAgent, securityAgent);

        // When
        Mono<Agent> result = taskDecomposer.selectBestAgent(subTask, candidates);

        // Then
        StepVerifier.create(result)
                .assertNext(selected -> {
                    assertThat(selected.getId()).isEqualTo("agent2"); // Security specialist
                })
                .verifyComplete();
    }

    @Test
    void shouldEstimateComplexity() {
        // Simple task
        double lowComplexity = taskDecomposer.estimateComplexity("Fix a typo");
        assertThat(lowComplexity).isLessThan(0.5);

        // Complex task
        double highComplexity = taskDecomposer.estimateComplexity(
                "Analyze and compare multiple data sources to synthesize a comprehensive " +
                "evaluation of the market trends, providing detailed insights and " +
                "recommendations for strategic decision-making across various business units.");
        assertThat(highComplexity).isGreaterThan(0.3);
    }

    @Test
    void shouldFallbackWhenDecompositionFails() {
        // Given
        String taskId = UUID.randomUUID().toString();
        
        TeamTask task = TeamTask.builder()
                .id(taskId)
                .input("Simple task")
                .build();
        
        AgentTeam team = AgentTeam.builder()
                .members(new ArrayList<>())
                .build();
        
        when(llmProviderRegistry.getDefaultProvider()).thenReturn(llmProvider);
        when(llmProvider.generate(any())).thenReturn(Mono.just(
                LLMResponse.builder().content("Invalid response without subtasks").build()));

        // When
        Mono<List<SubTask>> result = taskDecomposer.decompose(task, team);

        // Then
        StepVerifier.create(result)
                .assertNext(subtasks -> {
                    assertThat(subtasks).hasSize(1); // Fallback single subtask
                })
                .verifyComplete();
    }
}
