package com.z254.butterfly.aurora.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI documentation configuration for AURORA service.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8086}")
    private int serverPort;

    @Bean
    public OpenAPI auroraOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AURORA Self-Healing Service API")
                        .description("""
                                AURORA is the self-healing engine for the BUTTERFLY ecosystem.
                                
                                ## Features
                                
                                - **Root Cause Analysis (RCA)**: Causal reasoning from correlated anomalies
                                - **Auto-Remediation**: Governed, automated healing actions
                                - **Chaos Immunization**: Learning from incidents to prevent recurrence
                                - **Safety Controls**: Blast-radius limits, pre-checks, rollback management
                                
                                ## Integration
                                
                                AURORA integrates with:
                                - PERCEPTION: Consumes anomaly events
                                - PLATO: Governance approval
                                - SYNAPSE: Healing action execution
                                - CAPSULE: Evidence storage
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("BUTTERFLY Team")
                                .email("butterfly@254studioz.com")
                                .url("https://254carbon.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server"),
                        new Server()
                                .url("http://aurora-service:8086")
                                .description("Kubernetes service")
                ))
                .tags(List.of(
                        new Tag()
                                .name("Incidents")
                                .description("Incident management and querying"),
                        new Tag()
                                .name("RCA")
                                .description("Root Cause Analysis operations"),
                        new Tag()
                                .name("Remediation")
                                .description("Remediation execution and management"),
                        new Tag()
                                .name("Immunity")
                                .description("Chaos immunity rules and learning")
                ));
    }
}
