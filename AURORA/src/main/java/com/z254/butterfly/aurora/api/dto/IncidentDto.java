package com.z254.butterfly.aurora.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * DTO for incident representation in API responses.
 */
@Data
@Builder
public class IncidentDto {
    private String id;
    private String title;
    private String description;
    private String status;
    private int priority;
    private double severity;
    private String primaryComponent;
    private Set<String> affectedComponents;
    private int hypothesesCount;
    private int remediationsCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;
    private Long mttrMs;
    private String assignee;
    private String correlationId;
    private Set<String> tags;
}
