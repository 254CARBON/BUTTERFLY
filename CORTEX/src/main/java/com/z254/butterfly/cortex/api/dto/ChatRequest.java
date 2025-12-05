package com.z254.butterfly.cortex.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for chat endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Message is required")
    @Size(max = 50000, message = "Message must be less than 50000 characters")
    private String message;

    private String conversationId;

    private String agentId;

    private boolean stream;

    private Map<String, Object> context;

    private Map<String, Object> metadata;
}
