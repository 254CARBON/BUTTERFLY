package com.z254.butterfly.cortex.api.v1;

import com.z254.butterfly.cortex.CortexTestConfiguration;
import com.z254.butterfly.cortex.api.dto.AgentRequest;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.Agent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AgentController}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(CortexTestConfiguration.class)
class AgentControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private String createdAgentId;

    @BeforeEach
    void setUp() {
        // Create a test agent for use in tests
        AgentRequest request = new AgentRequest();
        request.setName("Integration Test Agent");
        request.setDescription("Agent created for integration testing");
        request.setType(CortexProperties.AgentType.REACT);
        request.setSystemPrompt("You are a helpful test agent.");
        request.setToolIds(Set.of("test-tool-1"));
        request.setNamespace("integration-test");

        Agent created = webTestClient.post()
                .uri("/api/v1/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Agent.class)
                .returnResult()
                .getResponseBody();

        if (created != null) {
            createdAgentId = created.getId();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/agents")
    class CreateAgentTests {

        @Test
        @DisplayName("should create agent with valid request")
        void createAgentWithValidRequest() {
            AgentRequest request = new AgentRequest();
            request.setName("New Agent");
            request.setDescription("A brand new agent");
            request.setType(CortexProperties.AgentType.PLAN_AND_EXECUTE);
            request.setSystemPrompt("You are a planning agent.");
            request.setNamespace("test");

            webTestClient.post()
                    .uri("/api/v1/agents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(Agent.class)
                    .value(agent -> {
                        assertThat(agent.getId()).isNotNull();
                        assertThat(agent.getName()).isEqualTo("New Agent");
                        assertThat(agent.getType()).isEqualTo(CortexProperties.AgentType.PLAN_AND_EXECUTE);
                        assertThat(agent.getStatus()).isEqualTo(Agent.AgentStatus.ACTIVE);
                        assertThat(agent.getCreatedAt()).isNotNull();
                    });
        }

        @Test
        @DisplayName("should create agent with all configuration options")
        void createAgentWithAllConfigOptions() {
            AgentRequest request = new AgentRequest();
            request.setName("Full Config Agent");
            request.setDescription("Agent with full configuration");
            request.setType(CortexProperties.AgentType.REACT);
            request.setSystemPrompt("You are a comprehensive agent.");
            request.setToolIds(Set.of("tool-1", "tool-2", "tool-3"));
            request.setNamespace("production");

            // Set memory config
            AgentRequest.MemoryConfig memConfig = new AgentRequest.MemoryConfig();
            memConfig.setEpisodicEnabled(true);
            memConfig.setMaxEpisodes(50);
            memConfig.setSemanticEnabled(true);
            request.setMemoryConfig(memConfig);

            // Set LLM config
            AgentRequest.LLMConfig llmConfig = new AgentRequest.LLMConfig();
            llmConfig.setProviderId("openai");
            llmConfig.setModel("gpt-4-turbo");
            llmConfig.setTemperature(0.5);
            llmConfig.setMaxTokens(4096);
            request.setLlmConfig(llmConfig);

            webTestClient.post()
                    .uri("/api/v1/agents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(Agent.class)
                    .value(agent -> {
                        assertThat(agent.getToolIds()).containsExactlyInAnyOrder("tool-1", "tool-2", "tool-3");
                        assertThat(agent.getNamespace()).isEqualTo("production");
                        assertThat(agent.getMemoryConfig()).isNotNull();
                        assertThat(agent.getLlmConfig()).isNotNull();
                        assertThat(agent.getLlmConfig().getModel()).isEqualTo("gpt-4-turbo");
                    });
        }
    }

    @Nested
    @DisplayName("GET /api/v1/agents/{id}")
    class GetAgentTests {

        @Test
        @DisplayName("should get existing agent by ID")
        void getExistingAgentById() {
            webTestClient.get()
                    .uri("/api/v1/agents/{id}", createdAgentId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Agent.class)
                    .value(agent -> {
                        assertThat(agent.getId()).isEqualTo(createdAgentId);
                        assertThat(agent.getName()).isEqualTo("Integration Test Agent");
                    });
        }

        @Test
        @DisplayName("should return 404 for non-existent agent")
        void return404ForNonExistentAgent() {
            webTestClient.get()
                    .uri("/api/v1/agents/{id}", "non-existent-id")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/agents")
    class ListAgentsTests {

        @Test
        @DisplayName("should list all agents")
        void listAllAgents() {
            webTestClient.get()
                    .uri("/api/v1/agents")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(Agent.class)
                    .value(agents -> {
                        assertThat(agents).isNotEmpty();
                    });
        }

        @Test
        @DisplayName("should filter agents by namespace")
        void filterAgentsByNamespace() {
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/agents")
                            .queryParam("namespace", "integration-test")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(Agent.class)
                    .value(agents -> {
                        assertThat(agents).isNotEmpty();
                        assertThat(agents).allMatch(a -> 
                                "integration-test".equals(a.getNamespace()));
                    });
        }

        @Test
        @DisplayName("should return empty list for non-existent namespace")
        void returnEmptyListForNonExistentNamespace() {
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/agents")
                            .queryParam("namespace", "non-existent-namespace")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(Agent.class)
                    .value(agents -> assertThat(agents).isEmpty());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/agents/{id}")
    class UpdateAgentTests {

        @Test
        @DisplayName("should update existing agent")
        void updateExistingAgent() {
            AgentRequest updateRequest = new AgentRequest();
            updateRequest.setName("Updated Agent Name");
            updateRequest.setDescription("Updated description");
            updateRequest.setType(CortexProperties.AgentType.REFLEXIVE);
            updateRequest.setSystemPrompt("Updated system prompt.");
            updateRequest.setNamespace("integration-test");

            webTestClient.put()
                    .uri("/api/v1/agents/{id}", createdAgentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updateRequest)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Agent.class)
                    .value(agent -> {
                        assertThat(agent.getId()).isEqualTo(createdAgentId);
                        assertThat(agent.getName()).isEqualTo("Updated Agent Name");
                        assertThat(agent.getDescription()).isEqualTo("Updated description");
                        assertThat(agent.getType()).isEqualTo(CortexProperties.AgentType.REFLEXIVE);
                    });
        }

        @Test
        @DisplayName("should return 404 when updating non-existent agent")
        void return404WhenUpdatingNonExistentAgent() {
            AgentRequest updateRequest = new AgentRequest();
            updateRequest.setName("Updated Name");
            updateRequest.setType(CortexProperties.AgentType.REACT);

            webTestClient.put()
                    .uri("/api/v1/agents/{id}", "non-existent-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updateRequest)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/agents/{id}")
    class DeleteAgentTests {

        @Test
        @DisplayName("should delete existing agent")
        void deleteExistingAgent() {
            // First create an agent to delete
            AgentRequest request = new AgentRequest();
            request.setName("Agent to Delete");
            request.setType(CortexProperties.AgentType.REACT);

            Agent created = webTestClient.post()
                    .uri("/api/v1/agents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(Agent.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(created).isNotNull();

            // Delete the agent
            webTestClient.delete()
                    .uri("/api/v1/agents/{id}", created.getId())
                    .exchange()
                    .expectStatus().isNoContent();

            // Verify it's deleted
            webTestClient.get()
                    .uri("/api/v1/agents/{id}", created.getId())
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("should return 404 when deleting non-existent agent")
        void return404WhenDeletingNonExistentAgent() {
            webTestClient.delete()
                    .uri("/api/v1/agents/{id}", "non-existent-id")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("Agent Status Operations")
    class AgentStatusTests {

        @Test
        @DisplayName("should suspend active agent")
        void suspendActiveAgent() {
            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/agents/{id}/suspend")
                            .queryParam("reason", "Test suspension")
                            .build(createdAgentId))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Agent.class)
                    .value(agent -> {
                        assertThat(agent.getStatus()).isEqualTo(Agent.AgentStatus.SUSPENDED);
                    });
        }

        @Test
        @DisplayName("should activate suspended agent")
        void activateSuspendedAgent() {
            // First suspend
            webTestClient.post()
                    .uri("/api/v1/agents/{id}/suspend", createdAgentId)
                    .exchange()
                    .expectStatus().isOk();

            // Then activate
            webTestClient.post()
                    .uri("/api/v1/agents/{id}/activate", createdAgentId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Agent.class)
                    .value(agent -> {
                        assertThat(agent.getStatus()).isEqualTo(Agent.AgentStatus.ACTIVE);
                    });
        }

        @Test
        @DisplayName("should return 404 when suspending non-existent agent")
        void return404WhenSuspendingNonExistentAgent() {
            webTestClient.post()
                    .uri("/api/v1/agents/{id}/suspend", "non-existent-id")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }
}
