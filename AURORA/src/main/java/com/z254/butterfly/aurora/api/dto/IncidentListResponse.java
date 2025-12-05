package com.z254.butterfly.aurora.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for incident list.
 */
@Data
@Builder
public class IncidentListResponse {
    private List<IncidentDto> incidents;
    private long total;
    private int page;
    private int size;
}
