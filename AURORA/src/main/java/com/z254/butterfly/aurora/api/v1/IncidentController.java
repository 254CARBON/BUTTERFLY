package com.z254.butterfly.aurora.api.v1;

import com.z254.butterfly.aurora.api.dto.IncidentDto;
import com.z254.butterfly.aurora.api.dto.IncidentListResponse;
import com.z254.butterfly.aurora.api.mapper.IncidentMapper;
import com.z254.butterfly.aurora.domain.model.Incident;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.domain.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

/**
 * REST API controller for incident management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents", description = "Incident management and querying")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    @Operation(summary = "List incidents", description = "List incidents with optional filters")
    public Mono<ResponseEntity<IncidentListResponse>> listIncidents(
            @Parameter(description = "Filter by status") 
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by component") 
            @RequestParam(required = false) String component,
            @Parameter(description = "Filter by priority (1-5)") 
            @RequestParam(required = false) Integer priority,
            @Parameter(description = "Page number") 
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") 
            @RequestParam(defaultValue = "20") int size) {

        return Mono.fromCallable(() -> {
            List<Incident> filtered = incidentService.listIncidents(status, component, priority);

            long total = filtered.size();
            List<Incident> paged = filtered.stream()
                    .skip((long) page * size)
                    .limit(size)
                    .toList();

            return ResponseEntity.ok(IncidentListResponse.builder()
                    .incidents(paged.stream().map(IncidentMapper::toDto).toList())
                    .total(total)
                    .page(page)
                    .size(size)
                    .build());
        });
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get incident", description = "Get incident details by ID")
    public Mono<ResponseEntity<IncidentDto>> getIncident(
            @Parameter(description = "Incident ID") @PathVariable String id) {

        return Mono.justOrEmpty(incidentService.getIncident(id))
                .map(IncidentMapper::toDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/rca")
    @Operation(summary = "Get incident RCA", description = "Get RCA results for an incident")
    public Mono<ResponseEntity<List<RcaHypothesis>>> getIncidentRca(
            @Parameter(description = "Incident ID") @PathVariable String id) {

        return Mono.justOrEmpty(incidentService.getIncident(id))
                .map(incident -> ResponseEntity.ok(incident.getHypotheses()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/remediations")
    @Operation(summary = "Get incident remediations", 
               description = "Get remediation history for an incident")
    public Mono<ResponseEntity<List<Incident.RemediationRecord>>> getIncidentRemediations(
            @Parameter(description = "Incident ID") @PathVariable String id) {

        return Mono.justOrEmpty(incidentService.getIncident(id))
                .map(incident -> ResponseEntity.ok(incident.getRemediationHistory()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Get incident timeline", description = "Get timeline events for an incident")
    public Mono<ResponseEntity<List<Incident.TimelineEvent>>> getIncidentTimeline(
            @Parameter(description = "Incident ID") @PathVariable String id) {

        return Mono.justOrEmpty(incidentService.getIncident(id))
                .map(incident -> ResponseEntity.ok(incident.getTimeline()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve incident", description = "Mark an incident as resolved")
    public Mono<ResponseEntity<IncidentDto>> resolveIncident(
            @Parameter(description = "Incident ID") @PathVariable String id,
            @RequestBody ResolveRequest request) {

        return incidentService.resolveIncident(id, request.getResolution())
                .map(IncidentMapper::toDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/escalate")
    @Operation(summary = "Escalate incident", description = "Escalate an incident")
    public Mono<ResponseEntity<IncidentDto>> escalateIncident(
            @Parameter(description = "Incident ID") @PathVariable String id,
            @RequestBody EscalateRequest request) {

        return incidentService.escalateIncident(id, request.getReason())
                .map(IncidentMapper::toDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/comment")
    @Operation(summary = "Add comment", description = "Add a comment to an incident")
    public Mono<ResponseEntity<IncidentDto>> addComment(
            @Parameter(description = "Incident ID") @PathVariable String id,
            @RequestBody CommentRequest request) {

        return incidentService.addComment(id, request.getComment())
                .map(IncidentMapper::toDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // ========== Request DTOs ==========

    @lombok.Data
    public static class ResolveRequest {
        private String resolution;
    }

    @lombok.Data
    public static class EscalateRequest {
        private String reason;
        private String assignTo;
    }

    @lombok.Data
    public static class CommentRequest {
        private String comment;
    }
}
