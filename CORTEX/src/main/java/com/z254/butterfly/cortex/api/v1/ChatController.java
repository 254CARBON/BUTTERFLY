package com.z254.butterfly.cortex.api.v1;

import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.api.dto.ChatRequest;
import com.z254.butterfly.cortex.api.dto.ChatResponse;
import com.z254.butterfly.cortex.domain.model.AgentThought;
import com.z254.butterfly.cortex.domain.model.Conversation;
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
 * REST controller for chat operations.
 */
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "Chat and conversation operations")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class ChatController {

    private final AgentService agentService;

    public ChatController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    @Operation(summary = "Send message", description = "Send a message and get a response")
    @ApiResponse(responseCode = "200", description = "Response generated")
    @PreAuthorize("hasAuthority('SCOPE_cortex:chat:write')")
    public Mono<ResponseEntity<ChatResponse>> sendMessage(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.info("Chat message from user {} to agent {}", userId, request.getAgentId());

        // Get or create conversation
        Mono<String> conversationIdMono;
        if (request.getConversationId() != null) {
            conversationIdMono = Mono.just(request.getConversationId());
        } else if (request.getAgentId() != null) {
            conversationIdMono = agentService.createConversation(request.getAgentId(), userId)
                    .map(Conversation::getId);
        } else {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return conversationIdMono
                .flatMap(conversationId ->
                        agentService.chatSync(conversationId, request.getMessage())
                                .map(result -> ChatResponse.fromResult(result, conversationId)))
                .map(ResponseEntity::ok);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send message with streaming", 
               description = "Send a message and stream the response")
    @ApiResponse(responseCode = "200", description = "Streaming response")
    @PreAuthorize("hasAuthority('SCOPE_cortex:chat:write')")
    public Flux<AgentThought> sendMessageStreaming(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.info("Streaming chat from user {} to agent {}", userId, request.getAgentId());

        // Get or create conversation
        Mono<String> conversationIdMono;
        if (request.getConversationId() != null) {
            conversationIdMono = Mono.just(request.getConversationId());
        } else if (request.getAgentId() != null) {
            conversationIdMono = agentService.createConversation(request.getAgentId(), userId)
                    .map(Conversation::getId);
        } else {
            return Flux.error(new IllegalArgumentException("Either conversationId or agentId is required"));
        }

        return conversationIdMono
                .flatMapMany(conversationId ->
                        agentService.chat(conversationId, request.getMessage()));
    }

    @GetMapping("/conversations")
    @Operation(summary = "List conversations", description = "List user's conversations")
    @ApiResponse(responseCode = "200", description = "Conversations retrieved")
    @PreAuthorize("hasAuthority('SCOPE_cortex:chat:read')")
    public Flux<Conversation> listConversations(
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        return agentService.listConversations(userId);
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get conversation", description = "Get conversation details and history")
    @ApiResponse(responseCode = "200", description = "Conversation found")
    @ApiResponse(responseCode = "404", description = "Conversation not found")
    @PreAuthorize("hasAuthority('SCOPE_cortex:chat:read')")
    public Mono<ResponseEntity<Conversation>> getConversation(
            @Parameter(description = "Conversation ID") @PathVariable String id) {
        
        return agentService.getConversation(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/conversations")
    @Operation(summary = "Create conversation", description = "Start a new conversation with an agent")
    @ApiResponse(responseCode = "201", description = "Conversation created")
    @PreAuthorize("hasAuthority('SCOPE_cortex:chat:write')")
    public Mono<ResponseEntity<Conversation>> createConversation(
            @RequestParam String agentId,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        log.info("Creating conversation with agent {} for user {}", agentId, userId);
        
        return agentService.createConversation(agentId, userId)
                .map(conv -> ResponseEntity.status(HttpStatus.CREATED).body(conv));
    }

    @PostMapping("/conversations/{id}/archive")
    @Operation(summary = "Archive conversation", description = "Archive a conversation")
    @ApiResponse(responseCode = "200", description = "Conversation archived")
    @PreAuthorize("hasAuthority('SCOPE_cortex:chat:write')")
    public Mono<ResponseEntity<Conversation>> archiveConversation(
            @Parameter(description = "Conversation ID") @PathVariable String id) {
        
        log.info("Archiving conversation: {}", id);
        
        return agentService.archiveConversation(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
