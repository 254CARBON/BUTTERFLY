package com.z254.butterfly.aurora.api.mapper;

import com.z254.butterfly.aurora.api.dto.IncidentDto;
import com.z254.butterfly.aurora.domain.model.Incident;

/**
 * Mapper for incident to DTO conversion.
 */
public final class IncidentMapper {

    private IncidentMapper() {}

    public static IncidentDto toDto(Incident incident) {
        return IncidentDto.builder()
                .id(incident.getId())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .status(incident.getStatus().name())
                .priority(incident.getPriority())
                .severity(incident.getSeverity())
                .primaryComponent(incident.getPrimaryComponent())
                .affectedComponents(incident.getAffectedComponents())
                .hypothesesCount(incident.getHypotheses().size())
                .remediationsCount(incident.getRemediationHistory().size())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .resolvedAt(incident.getResolvedAt())
                .mttrMs(incident.getMttrMs())
                .assignee(incident.getAssignee())
                .correlationId(incident.getCorrelationId())
                .tags(incident.getTags())
                .build();
    }
}
