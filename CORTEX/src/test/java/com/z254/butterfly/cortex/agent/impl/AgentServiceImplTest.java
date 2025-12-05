package com.z254.butterfly.cortex.agent.impl;

import com.z254.butterfly.cortex.agent.AgentExecutor;
import com.z254.butterfly.cortex.agent.AgentExecutorFactory;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.Agent;
import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.domain.model.AgentResult;
import com.z254.butterfly.cortex.domain.model.AgentTask;
import com.z254.butterfly.cortex.domain.model.AgentThought;
import com.z254.butterfly.cortex.domain.model.Conversation;
import com.z254.butterfly.cortex.domain.repository.AgentRepository;
import com.z254.butterfly.cortex.domain.repository.ConversationRepository;
import com.z254.butterfly.cortex.kafka.AgentResultProducer;
import com.z254.butterfly.cortex.llm.guardrails.TokenBudgetEnforcer;
import com.z254.butterfly.cortex.memory.EpisodicMemory;
import com.z254.butterfly.cortex.memory.capsule.CapsuleMemoryAdapter;
import com.z254.butterfly.cortex.resilience.ExecutionQuotaManager;
import com.z254.butterfly.cortex.resilience.ExecutionQuotaManager.QuotaCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock
    private AgentRepository agentRepository;
    
    @Mock
    private ConversationRepository conversationRepository;
    
    @Mock
    private AgentExecutorFactory executorFactory;
    
    @Mock
    private AgentExecutor agentExecutor;
    
    @Mock
    private TokenBudgetEnforcer tokenBudgetEnforcer;
    
    @Mock
    private ExecutionQuotaManager quotaManager;
    
    @Mock
    private EpisodicMemory episodicMemory;
    
    @Mock
    private CapsuleMemoryAdapter capsuleMemoryAdapter;
    
    @Mock
    private AgentResultProducer resultProducer;

    private AgentServiceImpl agentService;

    @BeforeEach
    void setUp() {
        agentService = new AgentServiceImpl(
                agentRepository,
                conversationRepository,
                executorFactory,
                tokenBudgetEnforcer,
                quotaManager,
                episodicMemory,
                capsuleMemoryAdapter,
                resultProducer
        );
    }

    @Nested
    @DisplayName("Agent Management")
    class AgentManagementTests {

        @Test
        @DisplayName("should create agent with generated ID")
        void createAgentWithGeneratedId() {
            Agent agent = Agent.builder()
                    .name("TestAgent")
                    .type(CortexProperties.AgentType.REACT)
                    .description("Test agent description")
                    .build();

            when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> 
                    Mono.just(inv.getArgument(0)));

            StepVerifier.create(agentService.createAgent(agent))
                    .assertNext(savedAgent -> {
                        assertThat(savedAgent.getId()).isNotNull();
                        assertThat(savedAgent.getName()).isEqualTo("TestAgent");
                        assertThat(savedAgent.getStatus()).isEqualTo(Agent.AgentStatus.ACTIVE);
                        assertThat(savedAgent.getCreatedAt()).isNotNull();
                        assertThat(savedAgent.getUpdatedAt()).isNotNull();
                    })
                    .verifyComplete();

            verify(agentRepository).save(any(Agent.class));
        }

        @Test
        @DisplayName("should get agent by ID")
        void getAgentById() {
            String agentId = "agent-123";
            Agent expected = Agent.builder()
                    .id(agentId)
                    .name("TestAgent")
                    .type(CortexProperties.AgentType.REACT)
                    .status(Agent.AgentStatus.ACTIVE)
                    .build();

            when(agentRepository.findById(agentId)).thenReturn(Mono.just(expected));

            StepVerifier.create(agentService.getAgent(agentId))
                    .assertNext(agent -> {
                        assertThat(agent.getId()).isEqualTo(agentId);
                        assertThat(agent.getName()).isEqualTo("TestAgent");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should update existing agent")
        void updateExistingAgent() {
            String agentId = "agent-123";
            Instant createdAt = Instant.now().minusSeconds(3600);
            
            Agent existingAgent = Agent.builder()
                    .id(agentId)
                    .name("OldName")
                    .type(CortexProperties.AgentType.REACT)
                    .status(Agent.AgentStatus.ACTIVE)
                    .createdAt(createdAt)
                    .build();

            Agent updateRequest = Agent.builder()
                    .name("NewName")
                    .description("Updated description")
                    .type(CortexProperties.AgentType.PLAN_AND_EXECUTE)
                    .build();

            when(agentRepository.findById(agentId)).thenReturn(Mono.just(existingAgent));
            when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> 
                    Mono.just(inv.getArgument(0)));

            StepVerifier.create(agentService.updateAgent(agentId, updateRequest))
                    .assertNext(updated -> {
                        assertThat(updated.getId()).isEqualTo(agentId);
                        assertThat(updated.getName()).isEqualTo("NewName");
                        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
                        assertThat(updated.getUpdatedAt()).isAfter(createdAt);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return error when updating non-existent agent")
        void updateNonExistentAgent() {
            String agentId = "non-existent";
            Agent updateRequest = Agent.builder().name("NewName").build();

            when(agentRepository.findById(agentId)).thenReturn(Mono.empty());

            StepVerifier.create(agentService.updateAgent(agentId, updateRequest))
                    .expectErrorMatches(e -> e instanceof IllegalArgumentException &&
                            e.getMessage().contains("Agent not found"))
                    .verify();
        }

        @Test
        @DisplayName("should delete agent by ID")
        void deleteAgentById() {
            String agentId = "agent-123";
            when(agentRepository.deleteById(agentId)).thenReturn(Mono.empty());

            StepVerifier.create(agentService.deleteAgent(agentId))
                    .verifyComplete();

            verify(agentRepository).deleteById(agentId);
        }

        @Test
        @DisplayName("should list all agents")
        void listAllAgents() {
            Agent agent1 = Agent.builder().id("1").name("Agent1").build();
            Agent agent2 = Agent.builder().id("2").name("Agent2").build();

            when(agentRepository.findAll()).thenReturn(Flux.just(agent1, agent2));

            StepVerifier.create(agentService.listAgents())
                    .expectNext(agent1)
                    .expectNext(agent2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("should list agents by namespace")
        void listAgentsByNamespace() {
            String namespace = "production";
            Agent agent = Agent.builder()
                    .id("1")
                    .name("ProdAgent")
                    .namespace(namespace)
                    .build();

            when(agentRepository.findByNamespace(namespace)).thenReturn(Flux.just(agent));

            StepVerifier.create(agentService.listAgentsByNamespace(namespace))
                    .assertNext(a -> {
                        assertThat(a.getNamespace()).isEqualTo(namespace);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Task Execution")
    class TaskExecutionTests {

        @Test
        @DisplayName("should submit task and return prepared task")
        void submitTask() {
            String agentId = "agent-123";
            Agent agent = Agent.builder()
                    .id(agentId)
                    .name("TestAgent")
                    .type(CortexProperties.AgentType.REACT)
                    .status(Agent.AgentStatus.ACTIVE)
                    .build();

            AgentTask task = AgentTask.builder()
                    .agentId(agentId)
                    .input("Test task input")
                    .build();

            when(agentRepository.findById(agentId)).thenReturn(Mono.just(agent));

            StepVerifier.create(agentService.submitTask(task))
                    .assertNext(submittedTask -> {
                        assertThat(submittedTask.getId()).isNotNull();
                        assertThat(submittedTask.getCorrelationId()).isNotNull();
                        assertThat(submittedTask.getIdempotencyKey()).isNotNull();
                        assertThat(submittedTask.getCreatedAt()).isNotNull();
                        assertThat(submittedTask.getStatus()).isEqualTo(AgentTask.TaskStatus.PENDING);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should reject task for non-existent agent")
        void submitTaskForNonExistentAgent() {
            String agentId = "non-existent";
            AgentTask task = AgentTask.builder()
                    .agentId(agentId)
                    .input("Test task input")
                    .build();

            when(agentRepository.findById(agentId)).thenReturn(Mono.empty());

            StepVerifier.create(agentService.submitTask(task))
                    .expectErrorMatches(e -> e instanceof IllegalArgumentException &&
                            e.getMessage().contains("Agent not found"))
                    .verify();
        }

        @Test
        @DisplayName("should execute task and return result")
        void executeTask() {
            String agentId = "agent-123";
            Agent agent = Agent.builder()
                    .id(agentId)
                    .name("TestAgent")
                    .type(CortexProperties.AgentType.REACT)
                    .status(Agent.AgentStatus.ACTIVE)
                    .toolIds(Set.of())
                    .build();

            AgentTask task = AgentTask.builder()
                    .agentId(agentId)
                    .input("Test task input")
                    .userId("user-1")
                    .namespace("default")
                    .build();

            AgentThought finalThought = AgentThought.builder()
                    .id("thought-1")
                    .taskId("task-123")
                    .agentId(agentId)
                    .type(AgentThought.ThoughtType.FINAL_ANSWER)
                    .content("This is the final answer")
                    .iteration(1)
                    .timestamp(Instant.now())
                    .build();

            when(agentRepository.findById(agentId)).thenReturn(Mono.just(agent));
            when(quotaManager.checkQuota(anyString(), anyString(), anyString()))
                    .thenReturn(Mono.just(QuotaCheckResult.allowed(100)));
            when(quotaManager.recordExecution(anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());
            when(tokenBudgetEnforcer.createTaskBudget(anyInt()))
                    .thenReturn(AgentContext.TokenBudget.builder()
                            .maxTokens(4000)
                            .usedTokens(0)
                            .build());
            when(executorFactory.getExecutor(CortexProperties.AgentType.REACT))
                    .thenReturn(agentExecutor);
            when(agentExecutor.execute(any(AgentTask.class), any(AgentContext.class)))
                    .thenReturn(Flux.just(finalThought));

            StepVerifier.create(agentService.executeTask(task))
                    .assertNext(result -> {
                        assertThat(result.getAgentId()).isEqualTo(agentId);
                        assertThat(result.getStatus()).isEqualTo(AgentResult.ResultStatus.SUCCESS);
                        assertThat(result.getOutput()).isEqualTo("This is the final answer");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should reject task when quota exceeded")
        void rejectTaskWhenQuotaExceeded() {
            String agentId = "agent-123";
            Agent agent = Agent.builder()
                    .id(agentId)
                    .name("TestAgent")
                    .type(CortexProperties.AgentType.REACT)
                    .status(Agent.AgentStatus.ACTIVE)
                    .toolIds(Set.of())
                    .build();

            AgentTask task = AgentTask.builder()
                    .agentId(agentId)
                    .input("Test task input")
                    .userId("user-1")
                    .namespace("default")
                    .build();

            when(agentRepository.findById(agentId)).thenReturn(Mono.just(agent));
            when(quotaManager.checkQuota(anyString(), anyString(), anyString()))
                    .thenReturn(Mono.just(QuotaCheckResult.exceeded(
                            ExecutionQuotaManager.QuotaType.USER, "Daily quota exceeded")));

            StepVerifier.create(agentService.executeTask(task))
                    .expectErrorMatches(e -> e instanceof IllegalStateException &&
                            e.getMessage().contains("Quota exceeded"))
                    .verify();
        }

        @Test
        @DisplayName("should reject task when agent is not active")
        void rejectTaskWhenAgentNotActive() {
            String agentId = "agent-123";
            Agent agent = Agent.builder()
                    .id(agentId)
                    .name("TestAgent")
                    .type(CortexProperties.AgentType.REACT)
                    .status(Agent.AgentStatus.SUSPENDED)
                    .build();

            AgentTask task = AgentTask.builder()
                    .agentId(agentId)
                    .input("Test task input")
                    .build();

            when(agentRepository.findById(agentId)).thenReturn(Mono.just(agent));
            when(quotaManager.checkQuota(any(), any(), any()))
                    .thenReturn(Mono.just(QuotaCheckResult.allowed(100)));

            StepVerifier.create(agentService.executeTask(task))
                    .expectErrorMatches(e -> e instanceof IllegalStateException &&
                            e.getMessage().contains("Agent is not active"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Conversation Management")
    class ConversationManagementTests {

        @Test
        @DisplayName("should create conversation for agent")
        void createConversation() {
            String agentId = "agent-123";
            String userId = "user-456";
            Agent agent = Agent.builder()
                    .id(agentId)
                    .name("TestAgent")
                    .namespace("default")
                    .build();

            when(agentRepository.findById(agentId)).thenReturn(Mono.just(agent));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(agentService.createConversation(agentId, userId))
                    .assertNext(conversation -> {
                        assertThat(conversation.getAgentId()).isEqualTo(agentId);
                        assertThat(conversation.getUserId()).isEqualTo(userId);
                        assertThat(conversation.getStatus())
                                .isEqualTo(Conversation.ConversationStatus.ACTIVE);
                        assertThat(conversation.getTitle()).contains("TestAgent");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should get conversation by ID")
        void getConversation() {
            String conversationId = "conv-123";
            Conversation expected = Conversation.builder()
                    .id(conversationId)
                    .agentId("agent-1")
                    .userId("user-1")
                    .status(Conversation.ConversationStatus.ACTIVE)
                    .build();

            when(conversationRepository.findById(conversationId))
                    .thenReturn(Mono.just(expected));

            StepVerifier.create(agentService.getConversation(conversationId))
                    .assertNext(conv -> {
                        assertThat(conv.getId()).isEqualTo(conversationId);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should archive conversation")
        void archiveConversation() {
            String conversationId = "conv-123";
            Conversation conversation = Conversation.builder()
                    .id(conversationId)
                    .agentId("agent-1")
                    .userId("user-1")
                    .status(Conversation.ConversationStatus.ACTIVE)
                    .build();

            when(conversationRepository.findById(conversationId))
                    .thenReturn(Mono.just(conversation));
            when(conversationRepository.save(any(Conversation.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(agentService.archiveConversation(conversationId))
                    .assertNext(archived -> {
                        assertThat(archived.getStatus())
                                .isEqualTo(Conversation.ConversationStatus.ARCHIVED);
                        assertThat(archived.getLastActiveAt()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should list conversations by user")
        void listConversationsByUser() {
            String userId = "user-123";
            Conversation conv1 = Conversation.builder()
                    .id("conv-1")
                    .userId(userId)
                    .build();
            Conversation conv2 = Conversation.builder()
                    .id("conv-2")
                    .userId(userId)
                    .build();

            when(conversationRepository.findByUserId(userId))
                    .thenReturn(Flux.just(conv1, conv2));

            StepVerifier.create(agentService.listConversations(userId))
                    .expectNext(conv1)
                    .expectNext(conv2)
                    .verifyComplete();
        }
    }
}
