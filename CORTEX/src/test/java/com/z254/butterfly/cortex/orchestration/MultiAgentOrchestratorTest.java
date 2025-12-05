package com.z254.butterfly.cortex.orchestration;

import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.domain.repository.AgentRepository;
import com.z254.butterfly.cortex.domain.repository.TeamRepository;
import com.z254.butterfly.cortex.orchestration.pattern.CoordinationPatternExecutor;
import com.z254.butterfly.cortex.orchestration.pattern.HierarchicalCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MultiAgentOrchestrator.
 */
@ExtendWith(MockitoExtension.class)
class MultiAgentOrchestratorTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentService agentService;

    @Mock
    private TaskDecomposer taskDecomposer;

    @Mock
    private ResultAggregator resultAggregator;

    @Mock
    private AgentMessageBus messageBus;

    private MultiAgentOrchestratorImpl orchestrator;
    private CortexProperties cortexProperties;

    @BeforeEach
    void setUp() {
        cortexProperties = new CortexProperties();
        cortexProperties.setMultiAgent(new CortexProperties.MultiAgentProperties());
        
        List<CoordinationPatternExecutor> executors = List.of(new HierarchicalCoordinator());
        
        orchestrator = new MultiAgentOrchestratorImpl(
                teamRepository,
                agentRepository,
                agentService,
                taskDecomposer,
                resultAggregator,
                messageBus,
                executors,
                cortexProperties
        );
    }

    @Test
    void shouldCreateTeam() {
        // Given
        AgentTeam team = AgentTeam.builder()
                .name("Test Team")
                .teamType(TeamType.HIERARCHICAL)
                .build();
        
        when(teamRepository.save(any(AgentTeam.class)))
                .thenAnswer(invocation -> {
                    AgentTeam t = invocation.getArgument(0);
                    t.setId(UUID.randomUUID().toString());
                    return Mono.just(t);
                });

        // When
        Mono<AgentTeam> result = orchestrator.createTeam(team);

        // Then
        StepVerifier.create(result)
                .assertNext(created -> {
                    assertThat(created.getId()).isNotNull();
                    assertThat(created.getName()).isEqualTo("Test Team");
                    assertThat(created.getStatus()).isEqualTo(TeamStatus.DRAFT);
                })
                .verifyComplete();
    }

    @Test
    void shouldGetTeam() {
        // Given
        String teamId = UUID.randomUUID().toString();
        AgentTeam team = AgentTeam.builder()
                .id(teamId)
                .name("Test Team")
                .build();
        
        when(teamRepository.findById(teamId)).thenReturn(Mono.just(team));

        // When
        Mono<AgentTeam> result = orchestrator.getTeam(teamId);

        // Then
        StepVerifier.create(result)
                .assertNext(found -> {
                    assertThat(found.getId()).isEqualTo(teamId);
                    assertThat(found.getName()).isEqualTo("Test Team");
                })
                .verifyComplete();
    }

    @Test
    void shouldListTeams() {
        // Given
        AgentTeam team1 = AgentTeam.builder().id("1").name("Team 1").build();
        AgentTeam team2 = AgentTeam.builder().id("2").name("Team 2").build();
        
        when(teamRepository.findAll()).thenReturn(Flux.just(team1, team2));

        // When
        Flux<AgentTeam> result = orchestrator.listTeams();

        // Then
        StepVerifier.create(result)
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldActivateTeam() {
        // Given
        String teamId = UUID.randomUUID().toString();
        String orchestratorAgentId = UUID.randomUUID().toString();
        
        AgentTeam team = AgentTeam.builder()
                .id(teamId)
                .name("Test Team")
                .teamType(TeamType.HIERARCHICAL)
                .orchestratorAgentId(orchestratorAgentId)
                .members(new ArrayList<>(List.of(TeamMember.orchestrator(orchestratorAgentId))))
                .status(TeamStatus.DRAFT)
                .build();
        
        when(teamRepository.findById(teamId)).thenReturn(Mono.just(team));
        when(teamRepository.save(any(AgentTeam.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        // When
        Mono<AgentTeam> result = orchestrator.activateTeam(teamId);

        // Then
        StepVerifier.create(result)
                .assertNext(activated -> {
                    assertThat(activated.getStatus()).isEqualTo(TeamStatus.ACTIVE);
                })
                .verifyComplete();
    }

    @Test
    void shouldFailActivationWhenTeamNotReady() {
        // Given
        String teamId = UUID.randomUUID().toString();
        
        AgentTeam team = AgentTeam.builder()
                .id(teamId)
                .name("Test Team")
                .teamType(TeamType.HIERARCHICAL)
                // No orchestrator or members
                .status(TeamStatus.DRAFT)
                .build();
        
        when(teamRepository.findById(teamId)).thenReturn(Mono.just(team));

        // When
        Mono<AgentTeam> result = orchestrator.activateTeam(teamId);

        // Then
        StepVerifier.create(result)
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void shouldSubmitTask() {
        // Given
        String teamId = UUID.randomUUID().toString();
        String orchestratorAgentId = UUID.randomUUID().toString();
        
        AgentTeam team = AgentTeam.builder()
                .id(teamId)
                .name("Test Team")
                .teamType(TeamType.HIERARCHICAL)
                .orchestratorAgentId(orchestratorAgentId)
                .members(new ArrayList<>(List.of(TeamMember.orchestrator(orchestratorAgentId))))
                .coordinationConfig(CoordinationConfig.hierarchical())
                .status(TeamStatus.ACTIVE)
                .build();
        
        TeamTask task = TeamTask.builder()
                .teamId(teamId)
                .input("Test input")
                .userId("user1")
                .build();
        
        when(teamRepository.findById(teamId)).thenReturn(Mono.just(team));

        // When
        Mono<TeamTask> result = orchestrator.submitTask(task);

        // Then
        StepVerifier.create(result)
                .assertNext(submitted -> {
                    assertThat(submitted.getId()).isNotNull();
                    assertThat(submitted.getTeamId()).isEqualTo(teamId);
                    assertThat(submitted.getInput()).isEqualTo("Test input");
                })
                .verifyComplete();
    }

    @Test
    void shouldFailTaskSubmissionWhenTeamNotReady() {
        // Given
        String teamId = UUID.randomUUID().toString();
        
        AgentTeam team = AgentTeam.builder()
                .id(teamId)
                .name("Test Team")
                .status(TeamStatus.DRAFT)
                .build();
        
        TeamTask task = TeamTask.builder()
                .teamId(teamId)
                .input("Test input")
                .build();
        
        when(teamRepository.findById(teamId)).thenReturn(Mono.just(team));

        // When
        Mono<TeamTask> result = orchestrator.submitTask(task);

        // Then
        StepVerifier.create(result)
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void shouldCancelTask() {
        // Given
        String taskId = UUID.randomUUID().toString();
        String teamId = UUID.randomUUID().toString();
        
        TeamTask task = TeamTask.builder()
                .id(taskId)
                .teamId(teamId)
                .input("Test input")
                .status(TeamTaskStatus.EXECUTING)
                .build();
        
        // Submit task first to store it
        orchestrator.submitTask(task).block();

        // When
        Mono<TeamTask> result = orchestrator.cancelTask(taskId, "Test cancellation");

        // Then
        StepVerifier.create(result)
                .assertNext(cancelled -> {
                    assertThat(cancelled.getStatus()).isEqualTo(TeamTaskStatus.CANCELLED);
                })
                .verifyComplete();
    }

    @Test
    void shouldDeleteTeam() {
        // Given
        String teamId = UUID.randomUUID().toString();
        
        when(teamRepository.deleteById(teamId)).thenReturn(Mono.empty());

        // When
        Mono<Void> result = orchestrator.deleteTeam(teamId);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(teamRepository).deleteById(teamId);
    }
}
