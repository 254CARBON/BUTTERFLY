package com.z254.butterfly.cortex.api.v1;

import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.api.dto.AgentRequest;
import com.z254.butterfly.cortex.domain.model.Agent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for agent management.
 */
@RestController
@RequestMapping("/api/v1/agents")
@Tag(name = "Agents", description = "Agent management operations")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    @Operation(summary = "Create agent", description = "Create a new agent")
    @ApiResponse(responseCode = "201", description = "Agent created successfully")
    @PreAuthorize("hasAuthority('SCOPE_cortex:agent:write')")
    public Mono<ResponseEntity<Agent>> createAgent(
            @Valid @RequestBody AgentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.info("Creating agent: {} by user {}", request.getName(), userId);
        
        Agent agent = request.toAgent(userId);
        
        return agentService.createAgent(agent)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .doOnSuccess(r -> log.info("Created agent: {}", r.getBody().getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get agent", description = "Get an agent by ID")
    @ApiResponse(responseCode = "200", description = "Agent found")
    @ApiResponse(responseCode = "404", description = "Agent not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:agent:read')")
    public Mono<ResponseEntity<Agent>> getAgent(
            @Parameter(description = "Agent ID") @PathVariable String id) {
        
        return agentService.getAgent(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "List agents", description = "List all agents")
    @ApiResponse(responseCode = "200", description = "Agents retrieved")
    @PreAuthorize("hasAuthority('SCOPE_cortex:agent:read')")
    public Flux<Agent> listAgents(
            @RequestParam(required = false) String namespace) {
        
        if (namespace != null) {
            return agentService.listAgentsByNamespace(namespace);
        }
        return agentService.listAgents();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update agent", description = "Update an existing agent")
    @ApiResponse(responseCode = "200", description = "Agent updated")
    @ApiResponse(responseCode = "404", description = "Agent not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:agent:write')")
    public Mono<ResponseEntity<Agent>> updateAgent(
            @Parameter(description = "Agent ID") @PathVariable String id,
            @Valid @RequestBody AgentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.info("Updating agent: {} by user {}", id, userId);
        
        return agentService.getAgent(id)
                .flatMap(existing -> {
                    Agent updated = request.toAgent(existing.getOwner());
                    updated.setId(id);
                    updated.setCreatedAt(existing.getCreatedAt());
                    updated.setTaskCount(existing.getTaskCount());
                    updated.setSuccessRate(existing.getSuccessRate());
                    return agentService.updateAgent(id, updated);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete agent", description = "Delete an agent")
    @ApiResponse(responseCode = "204", description = "Agent deleted")
    @ApiResponse(responseCode = "404", description = "Agent not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:agent:delete')")
    public Mono<ResponseEntity<Void>> deleteAgent(
            @Parameter(description = "Agent ID") @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.info("Deleting agent: {} by user {}", id, userId);
        
        return agentService.getAgent(id)
                .flatMap(agent -> agentService.deleteAgent(id).thenReturn(agent))
                .map(agent -> ResponseEntity.noContent().<Void>build())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate agent", description = "Activate a suspended agent")
    @PreAuthorize("hasAuthority('SCOPE_cortex:agent:write')")
    public Mono<ResponseEntity<Agent>> activateAgent(
            @PathVariable String id) {
        
        return agentService.getAgent(id)
                .flatMap(agent -> {
                    agent.setStatus(Agent.AgentStatus.ACTIVE);
                    return agentService.updateAgent(id, agent);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend agent", description = "Suspend an active agent")
    @PreAuthorize("hasAuthority('SCOPE_cortex:agent:write')")
    public Mono<ResponseEntity<Agent>> suspendAgent(
            @PathVariable String id,
            @RequestParam(required = false) String reason) {
        
        log.info("Suspending agent: {} - {}", id, reason);
        
        return agentService.getAgent(id)
                .flatMap(agent -> {
                    agent.setStatus(Agent.AgentStatus.SUSPENDED);
                    return agentService.updateAgent(id, agent);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
