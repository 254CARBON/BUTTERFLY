package com.z254.butterfly.cortex.api.v1;

import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.api.dto.AgentTaskRequest;
import com.z254.butterfly.cortex.domain.model.AgentResult;
import com.z254.butterfly.cortex.domain.model.AgentTask;
import com.z254.butterfly.cortex.domain.model.AgentThought;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for agent task operations.
 */
@RestController
@RequestMapping("/api/v1/agent-tasks")
@Tag(name = "Agent Tasks", description = "Agent task execution operations")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class AgentTaskController {

    private final AgentService agentService;

    public AgentTaskController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    @Operation(summary = "Submit task", description = "Submit a new task for execution")
    @ApiResponse(responseCode = "202", description = "Task submitted")
    @PreAuthorize("hasAuthority('SCOPE_cortex:task:write')")
    public Mono<ResponseEntity<AgentTask>> submitTask(
            @Valid @RequestBody AgentTaskRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        String namespace = jwt.getClaimAsString("namespace");
        
        log.info("Submitting task for agent {} by user {}", request.getAgentId(), userId);
        
        AgentTask task = request.toTask(userId, namespace);
        
        return agentService.submitTask(task)
                .map(submitted -> ResponseEntity.status(HttpStatus.ACCEPTED).body(submitted))
                .doOnSuccess(r -> log.info("Submitted task: {}", r.getBody().getId()));
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute task synchronously", 
               description = "Execute a task and wait for the result")
    @ApiResponse(responseCode = "200", description = "Task completed")
    @PreAuthorize("hasAuthority('SCOPE_cortex:task:write')")
    public Mono<ResponseEntity<AgentResult>> executeTask(
            @Valid @RequestBody AgentTaskRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        String namespace = jwt.getClaimAsString("namespace");
        
        log.info("Executing task synchronously for agent {} by user {}", 
                request.getAgentId(), userId);
        
        AgentTask task = request.toTask(userId, namespace);
        
        return agentService.executeTask(task)
                .map(ResponseEntity::ok)
                .doOnSuccess(r -> log.info("Completed task: {} - {}", 
                        r.getBody().getTaskId(), r.getBody().getStatus()));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Execute task with streaming", 
               description = "Execute a task and stream thoughts in real-time")
    @ApiResponse(responseCode = "200", description = "Streaming task execution")
    @PreAuthorize("hasAuthority('SCOPE_cortex:task:write')")
    public Flux<AgentThought> executeTaskStreaming(
            @Valid @RequestBody AgentTaskRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        String namespace = jwt.getClaimAsString("namespace");
        
        log.info("Executing task with streaming for agent {} by user {}", 
                request.getAgentId(), userId);
        
        AgentTask task = request.toTask(userId, namespace);
        
        return agentService.executeTaskStreaming(task)
                .doOnComplete(() -> log.info("Completed streaming task: {}", task.getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task", description = "Get task status and details")
    @ApiResponse(responseCode = "200", description = "Task found")
    @ApiResponse(responseCode = "404", description = "Task not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:task:read')")
    public Mono<ResponseEntity<AgentTask>> getTask(
            @Parameter(description = "Task ID") @PathVariable String id) {
        
        return agentService.getTask(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/result")
    @Operation(summary = "Get task result", description = "Get the result of a completed task")
    @ApiResponse(responseCode = "200", description = "Result found")
    @ApiResponse(responseCode = "404", description = "Result not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:task:read')")
    public Mono<ResponseEntity<AgentResult>> getTaskResult(
            @Parameter(description = "Task ID") @PathVariable String id) {
        
        return agentService.getTaskResult(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel task", description = "Cancel a running task")
    @ApiResponse(responseCode = "200", description = "Task cancelled")
    @ApiResponse(responseCode = "404", description = "Task not found")
    @ApiResponse(responseCode = "409", description = "Task cannot be cancelled")
    @PreAuthorize("hasAuthority('SCOPE_cortex:task:write')")
    public Mono<ResponseEntity<AgentTask>> cancelTask(
            @Parameter(description = "Task ID") @PathVariable String id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.info("Cancelling task {} by user {}: {}", id, userId, reason);
        
        return agentService.cancelTask(id, reason != null ? reason : "Cancelled by user")
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
