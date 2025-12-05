package com.z254.butterfly.cortex.api.v1;

import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.domain.model.Conversation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * REST controller for conversation management.
 * Conversations provide context continuity across multiple tasks with an agent.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations", description = "Conversation management for contextual agent interactions")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class ConversationController {

    private final AgentService agentService;

    public ConversationController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    @Operation(
            summary = "Create conversation",
            description = "Create a new conversation with an agent. Conversations maintain context across multiple tasks.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Conversation created successfully",
                    content = @Content(schema = @Schema(implementation = Conversation.class))),
            @ApiResponse(responseCode = "400", description = "Invalid agent ID"),
            @ApiResponse(responseCode = "404", description = "Agent not found")
    })
    @PreAuthorize("hasAuthority('SCOPE_cortex:conversation:write')")
    public Mono<ResponseEntity<Conversation>> createConversation(
            @Parameter(description = "ID of the agent to converse with", required = true)
            @RequestParam String agentId,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.info("Creating conversation with agent {} for user {}", agentId, userId);
        
        return agentService.createConversation(agentId, userId)
                .map(conv -> ResponseEntity.status(HttpStatus.CREATED).body(conv))
                .doOnSuccess(r -> log.info("Created conversation: {}", r.getBody().getId()));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get conversation",
            description = "Get conversation details including status, message count, and metadata")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conversation found",
                    content = @Content(schema = @Schema(implementation = Conversation.class))),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    @PreAuthorize("hasAuthority('SCOPE_cortex:conversation:read')")
    public Mono<ResponseEntity<Conversation>> getConversation(
            @Parameter(description = "Conversation ID") @PathVariable String id) {
        
        return agentService.getConversation(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(
            summary = "List conversations",
            description = "List all conversations for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Conversations retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = Conversation.class))))
    @PreAuthorize("hasAuthority('SCOPE_cortex:conversation:read')")
    public Flux<Conversation> listConversations(
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        return agentService.listConversations(userId);
    }

    @GetMapping("/agent/{agentId}")
    @Operation(
            summary = "List conversations by agent",
            description = "List all conversations with a specific agent for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Conversations retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = Conversation.class))))
    @PreAuthorize("hasAuthority('SCOPE_cortex:conversation:read')")
    public Flux<Conversation> listConversationsByAgent(
            @Parameter(description = "Agent ID") @PathVariable String agentId,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        return agentService.listConversations(userId)
                .filter(conv -> agentId.equals(conv.getAgentId()));
    }

    @PostMapping("/{id}/archive")
    @Operation(
            summary = "Archive conversation",
            description = "Archive a conversation. Archived conversations are retained but no longer active.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conversation archived",
                    content = @Content(schema = @Schema(implementation = Conversation.class))),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    @PreAuthorize("hasAuthority('SCOPE_cortex:conversation:write')")
    public Mono<ResponseEntity<Conversation>> archiveConversation(
            @Parameter(description = "Conversation ID") @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.info("Archiving conversation {} by user {}", id, userId);
        
        return agentService.archiveConversation(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete conversation",
            description = "Permanently delete a conversation and its history")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Conversation deleted"),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    @PreAuthorize("hasAuthority('SCOPE_cortex:conversation:delete')")
    public Mono<ResponseEntity<Void>> deleteConversation(
            @Parameter(description = "Conversation ID") @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.info("Deleting conversation {} by user {}", id, userId);
        
        return agentService.getConversation(id)
                .flatMap(conv -> agentService.deleteConversation(id).thenReturn(conv))
                .map(conv -> ResponseEntity.noContent().<Void>build())
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
