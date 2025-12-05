package com.z254.butterfly.cortex.api.v1;

import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.orchestration.AgentMessageBus;
import com.z254.butterfly.cortex.orchestration.MultiAgentOrchestrator;
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

import java.util.Map;

/**
 * REST controller for multi-agent team management and task execution.
 */
@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Multi-agent team management and orchestration")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class TeamController {

    private final MultiAgentOrchestrator orchestrator;
    private final AgentMessageBus messageBus;

    public TeamController(MultiAgentOrchestrator orchestrator, AgentMessageBus messageBus) {
        this.orchestrator = orchestrator;
        this.messageBus = messageBus;
    }

    // --------------------------------------------------------------------------------------------
    // Team CRUD Operations
    // --------------------------------------------------------------------------------------------

    @PostMapping
    @Operation(summary = "Create team", description = "Create a new agent team")
    @ApiResponse(responseCode = "201", description = "Team created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid team configuration")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:write')")
    public Mono<ResponseEntity<AgentTeam>> createTeam(
            @Valid @RequestBody AgentTeam team,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt != null ? jwt.getSubject() : "anonymous";
        log.info("Creating team: {} by user {}", team.getName(), userId);
        
        team.setOwner(userId);
        
        return orchestrator.createTeam(team)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .doOnSuccess(r -> log.info("Created team: {}", r.getBody().getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get team", description = "Get a team by ID")
    @ApiResponse(responseCode = "200", description = "Team found")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:read')")
    public Mono<ResponseEntity<AgentTeam>> getTeam(
            @Parameter(description = "Team ID") @PathVariable String id) {
        
        return orchestrator.getTeam(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "List teams", description = "List all teams")
    @ApiResponse(responseCode = "200", description = "Teams retrieved")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:read')")
    public Flux<AgentTeam> listTeams(
            @RequestParam(required = false) String namespace) {
        
        if (namespace != null) {
            return orchestrator.listTeamsByNamespace(namespace);
        }
        return orchestrator.listTeams();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update team", description = "Update an existing team")
    @ApiResponse(responseCode = "200", description = "Team updated")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:write')")
    public Mono<ResponseEntity<AgentTeam>> updateTeam(
            @Parameter(description = "Team ID") @PathVariable String id,
            @Valid @RequestBody AgentTeam team,
            @AuthenticationPrincipal Jwt jwt) {
        
        log.info("Updating team: {}", id);
        
        return orchestrator.updateTeam(id, team)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete team", description = "Delete a team")
    @ApiResponse(responseCode = "204", description = "Team deleted")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:write')")
    public Mono<ResponseEntity<Void>> deleteTeam(
            @Parameter(description = "Team ID") @PathVariable String id) {
        
        log.info("Deleting team: {}", id);
        
        return orchestrator.deleteTeam(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // --------------------------------------------------------------------------------------------
    // Team Status Management
    // --------------------------------------------------------------------------------------------

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate team", description = "Activate a team to accept tasks")
    @ApiResponse(responseCode = "200", description = "Team activated")
    @ApiResponse(responseCode = "400", description = "Team cannot be activated")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:write')")
    public Mono<ResponseEntity<AgentTeam>> activateTeam(
            @Parameter(description = "Team ID") @PathVariable String id) {
        
        log.info("Activating team: {}", id);
        
        return orchestrator.activateTeam(id)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalStateException.class, 
                        e -> Mono.just(ResponseEntity.badRequest().build()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause team", description = "Pause a team from accepting tasks")
    @ApiResponse(responseCode = "200", description = "Team paused")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:write')")
    public Mono<ResponseEntity<AgentTeam>> pauseTeam(
            @Parameter(description = "Team ID") @PathVariable String id) {
        
        log.info("Pausing team: {}", id);
        
        return orchestrator.pauseTeam(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // --------------------------------------------------------------------------------------------
    // Team Member Management
    // --------------------------------------------------------------------------------------------

    @PostMapping("/{id}/members")
    @Operation(summary = "Add member", description = "Add a member to the team")
    @ApiResponse(responseCode = "200", description = "Member added")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:write')")
    public Mono<ResponseEntity<AgentTeam>> addMember(
            @Parameter(description = "Team ID") @PathVariable String id,
            @Valid @RequestBody TeamMember member) {
        
        log.info("Adding member {} to team {}", member.getAgentId(), id);
        
        return orchestrator.getTeam(id)
                .flatMap(team -> {
                    team.addMember(member);
                    return orchestrator.updateTeam(id, team);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/members/{agentId}")
    @Operation(summary = "Remove member", description = "Remove a member from the team")
    @ApiResponse(responseCode = "200", description = "Member removed")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:write')")
    public Mono<ResponseEntity<AgentTeam>> removeMember(
            @Parameter(description = "Team ID") @PathVariable String id,
            @Parameter(description = "Agent ID") @PathVariable String agentId) {
        
        log.info("Removing member {} from team {}", agentId, id);
        
        return orchestrator.getTeam(id)
                .flatMap(team -> {
                    team.removeMember(agentId);
                    return orchestrator.updateTeam(id, team);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // --------------------------------------------------------------------------------------------
    // Task Execution
    // --------------------------------------------------------------------------------------------

    @PostMapping("/{id}/tasks")
    @Operation(summary = "Submit task", description = "Submit a task for team execution")
    @ApiResponse(responseCode = "201", description = "Task submitted")
    @ApiResponse(responseCode = "400", description = "Invalid task or team not ready")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:execute')")
    public Mono<ResponseEntity<TeamTask>> submitTask(
            @Parameter(description = "Team ID") @PathVariable String id,
            @Valid @RequestBody TeamTaskRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt != null ? jwt.getSubject() : "anonymous";
        log.info("Submitting task to team {} by user {}", id, userId);
        
        TeamTask task = TeamTask.create(id, request.getInput(), userId, request.getContext());
        if (request.getTimeout() != null) {
            task.setTimeout(request.getTimeout());
        }
        if (request.getTokenBudget() != null) {
            task.setTokenBudget(request.getTokenBudget());
        }
        
        return orchestrator.submitTask(task)
                .map(submitted -> ResponseEntity.status(HttpStatus.CREATED).body(submitted))
                .onErrorResume(IllegalStateException.class,
                        e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @PostMapping("/{id}/tasks/execute")
    @Operation(summary = "Execute task", description = "Submit and execute a task, returning the result")
    @ApiResponse(responseCode = "200", description = "Task executed")
    @ApiResponse(responseCode = "400", description = "Invalid task or team not ready")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:execute')")
    public Mono<ResponseEntity<TeamResult>> executeTask(
            @Parameter(description = "Team ID") @PathVariable String id,
            @Valid @RequestBody TeamTaskRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt != null ? jwt.getSubject() : "anonymous";
        log.info("Executing task on team {} by user {}", id, userId);
        
        TeamTask task = TeamTask.create(id, request.getInput(), userId, request.getContext());
        
        return orchestrator.executeTask(task)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalStateException.class,
                        e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping(value = "/{id}/tasks/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream task execution", description = "Stream thoughts from task execution via SSE")
    @ApiResponse(responseCode = "200", description = "Streaming task thoughts")
    @ApiResponse(responseCode = "404", description = "Task not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:read')")
    public Flux<TeamThought> streamTask(
            @Parameter(description = "Team ID") @PathVariable String id,
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        
        log.info("Streaming task {} for team {}", taskId, id);
        
        return orchestrator.getTask(taskId)
                .flatMapMany(task -> {
                    TeamTask newTask = TeamTask.create(id, task.getInput(), task.getUserId());
                    return orchestrator.executeTaskStreaming(newTask);
                });
    }

    @GetMapping("/{id}/tasks")
    @Operation(summary = "List tasks", description = "List tasks for a team")
    @ApiResponse(responseCode = "200", description = "Tasks retrieved")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:read')")
    public Flux<TeamTask> listTasks(
            @Parameter(description = "Team ID") @PathVariable String id) {
        
        return orchestrator.listTasksForTeam(id);
    }

    @GetMapping("/{id}/tasks/{taskId}")
    @Operation(summary = "Get task", description = "Get a task by ID")
    @ApiResponse(responseCode = "200", description = "Task found")
    @ApiResponse(responseCode = "404", description = "Task not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:read')")
    public Mono<ResponseEntity<TeamTask>> getTask(
            @Parameter(description = "Team ID") @PathVariable String id,
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        
        return orchestrator.getTask(taskId)
                .filter(task -> id.equals(task.getTeamId()))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/tasks/{taskId}/result")
    @Operation(summary = "Get task result", description = "Get the result of a task")
    @ApiResponse(responseCode = "200", description = "Result found")
    @ApiResponse(responseCode = "404", description = "Result not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:read')")
    public Mono<ResponseEntity<TeamResult>> getTaskResult(
            @Parameter(description = "Team ID") @PathVariable String id,
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        
        return orchestrator.getTaskResult(taskId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/tasks/{taskId}/cancel")
    @Operation(summary = "Cancel task", description = "Cancel a running task")
    @ApiResponse(responseCode = "200", description = "Task cancelled")
    @ApiResponse(responseCode = "404", description = "Task not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:execute')")
    public Mono<ResponseEntity<TeamTask>> cancelTask(
            @Parameter(description = "Team ID") @PathVariable String id,
            @Parameter(description = "Task ID") @PathVariable String taskId,
            @RequestParam(defaultValue = "Cancelled by user") String reason) {
        
        log.info("Cancelling task {} for team {} - {}", taskId, id, reason);
        
        return orchestrator.cancelTask(taskId, reason)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // --------------------------------------------------------------------------------------------
    // Message Bus
    // --------------------------------------------------------------------------------------------

    @GetMapping("/{id}/tasks/{taskId}/messages")
    @Operation(summary = "Get messages", description = "Get inter-agent messages for a task")
    @ApiResponse(responseCode = "200", description = "Messages retrieved")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:read')")
    public Flux<AgentMessage> getTaskMessages(
            @Parameter(description = "Team ID") @PathVariable String id,
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        
        return messageBus.getConversation(taskId);
    }

    @GetMapping(value = "/{id}/tasks/{taskId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream messages", description = "Stream inter-agent messages via SSE")
    @ApiResponse(responseCode = "200", description = "Streaming messages")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:read')")
    public Flux<AgentMessage> streamTaskMessages(
            @Parameter(description = "Team ID") @PathVariable String id,
            @Parameter(description = "Task ID") @PathVariable String taskId) {
        
        return messageBus.subscribeToTask(taskId);
    }

    // --------------------------------------------------------------------------------------------
    // Statistics
    // --------------------------------------------------------------------------------------------

    @GetMapping("/{id}/stats")
    @Operation(summary = "Get team stats", description = "Get team performance statistics")
    @ApiResponse(responseCode = "200", description = "Stats retrieved")
    @ApiResponse(responseCode = "404", description = "Team not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:team:read')")
    public Mono<ResponseEntity<Map<String, Object>>> getTeamStats(
            @Parameter(description = "Team ID") @PathVariable String id) {
        
        return orchestrator.getTeam(id)
                .map(team -> {
                    Map<String, Object> stats = new java.util.HashMap<>();
                    stats.put("teamId", team.getId());
                    stats.put("name", team.getName());
                    stats.put("memberCount", team.getMemberCount());
                    stats.put("status", team.getStatus());
                    stats.put("totalTasks", team.getTotalTasks());
                    stats.put("successfulTasks", team.getSuccessfulTasks());
                    stats.put("successRate", team.getSuccessRate());
                    stats.put("avgTaskDurationMs", team.getAvgTaskDurationMs());
                    return ResponseEntity.ok(stats);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // --------------------------------------------------------------------------------------------
    // Request DTOs
    // --------------------------------------------------------------------------------------------

    /**
     * Request DTO for submitting team tasks.
     */
    public static class TeamTaskRequest {
        private String input;
        private Map<String, Object> context;
        private java.time.Duration timeout;
        private Integer tokenBudget;

        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        public Map<String, Object> getContext() { return context; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        public java.time.Duration getTimeout() { return timeout; }
        public void setTimeout(java.time.Duration timeout) { this.timeout = timeout; }
        public Integer getTokenBudget() { return tokenBudget; }
        public void setTokenBudget(Integer tokenBudget) { this.tokenBudget = tokenBudget; }
    }
}
