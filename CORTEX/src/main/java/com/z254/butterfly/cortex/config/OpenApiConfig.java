package com.z254.butterfly.cortex.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for CORTEX service.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cortexOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CORTEX API")
                        .description("""
                                CORTEX - AI Agent Orchestration Layer for the BUTTERFLY Ecosystem.
                                
                                Provides intelligent agents that can reason, plan, use tools, and maintain memory.
                                
                                ## Features
                                - **Agent Framework**: ReAct, PlanAndExecute, and Reflexive agents
                                - **LLM Abstraction**: Unified interface for OpenAI, Anthropic, Ollama
                                - **Memory Systems**: Episodic, semantic, and working memory
                                - **Tool Interface**: Bridge to SYNAPSE for governed tool execution
                                - **Streaming**: Real-time thought streaming via WebSocket/SSE
                                
                                ## Authentication
                                All endpoints (except health checks) require JWT authentication.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("254STUDIOZ Engineering")
                                .email("engineering@254carbon.com")
                                .url("https://254carbon.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://254carbon.com/licenses")))
                .servers(List.of(
                        new Server().url("/").description("Current server"),
                        new Server().url("http://localhost:8086").description("Local development")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token from identity provider")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
