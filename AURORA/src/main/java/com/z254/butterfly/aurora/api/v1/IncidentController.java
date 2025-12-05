package com.z254.butterfly.aurora.api.v1;

import com.z254.butterfly.aurora.api.dto.IncidentDto;
import com.z254.butterfly.aurora.api.dto.IncidentListResponse;
import com.z254.butterfly.aurora.api.mapper.IncidentMapper;
import com.z254.butterfly.aurora.domain.model.Incident;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API controller for incident management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents", description = "Incident management and querying")
public class IncidentController {

    // In-memory storage (replace with repository in production)
    private final Map<String, Incident> incidents = new ConcurrentHashMap<>();

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
            List<Incident> filtered = incidents.values().stream()
                    .filter(i -> status == null || i.getStatus().name().equals(status))
                    .filter(i -> component == null || 
                            i.getPrimaryComponent().contains(component) ||
                            i.getAffectedComponents().stream().anyMatch(c -> c.contains(component)))
                    .filter(i -> priority == null || i.getPriority() == priority)
                    .sorted(Comparator.comparing(Incident::getCreatedAt).reversed())
                    .skip((long) page * size)
                    .limit(size)
                    .toList();

            long total = incidents.values().stream()
                    .filter(i -> status == null || i.getStatus().name().equals(status))
                    .count();

            return ResponseEntity.ok(IncidentListResponse.builder()
                    .incidents(filtered.stream().map(IncidentMapper::toDto).toList())
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

        return Mono.justOrEmpty(incidents.get(id))
                .map(IncidentMapper::toDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/rca")
    @Operation(summary = "Get incident RCA", description = "Get RCA results for an incident")
    public Mono<ResponseEntity<List<RcaHypothesis>>> getIncidentRca(
            @Parameter(description = "Incident ID") @PathVariable String id) {

        return Mono.justOrEmpty(incidents.get(id))
                .map(incident -> ResponseEntity.ok(incident.getHypotheses()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/remediations")
    @Operation(summary = "Get incident remediations", 
               description = "Get remediation history for an incident")
    public Mono<ResponseEntity<List<Incident.RemediationRecord>>> getIncidentRemediations(
            @Parameter(description = "Incident ID") @PathVariable String id) {

        return Mono.justOrEmpty(incidents.get(id))
                .map(incident -> ResponseEntity.ok(incident.getRemediationHistory()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Get incident timeline", description = "Get timeline events for an incident")
    public Mono<ResponseEntity<List<Incident.TimelineEvent>>> getIncidentTimeline(
            @Parameter(description = "Incident ID") @PathVariable String id) {

        return Mono.justOrEmpty(incidents.get(id))
                .map(incident -> ResponseEntity.ok(incident.getTimeline()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve incident", description = "Mark an incident as resolved")
    public Mono<ResponseEntity<IncidentDto>> resolveIncident(
            @Parameter(description = "Incident ID") @PathVariable String id,
            @RequestBody ResolveRequest request) {

        return Mono.justOrEmpty(incidents.get(id))
                .map(incident -> {
                    incident.resolve(request.getResolution());
                    return ResponseEntity.ok(IncidentMapper.toDto(incident));
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/escalate")
    @Operation(summary = "Escalate incident", description = "Escalate an incident")
    public Mono<ResponseEntity<IncidentDto>> escalateIncident(
            @Parameter(description = "Incident ID") @PathVariable String id,
            @RequestBody EscalateRequest request) {

        return Mono.justOrEmpty(incidents.get(id))
                .map(incident -> {
                    incident.setStatus(Incident.IncidentStatus.ESCALATED);
                    incident.addTimelineEvent(Incident.TimelineEventType.ESCALATED, 
                            request.getReason());
                    return ResponseEntity.ok(IncidentMapper.toDto(incident));
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/comment")
    @Operation(summary = "Add comment", description = "Add a comment to an incident")
    public Mono<ResponseEntity<IncidentDto>> addComment(
            @Parameter(description = "Incident ID") @PathVariable String id,
            @RequestBody CommentRequest request) {

        return Mono.justOrEmpty(incidents.get(id))
                .map(incident -> {
                    incident.addTimelineEvent(Incident.TimelineEventType.COMMENT, 
                            request.getComment());
                    return ResponseEntity.ok(IncidentMapper.toDto(incident));
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // Internal method to store incidents (called by stream processor)
    public void storeIncident(Incident incident) {
        incidents.put(incident.getId(), incident);
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
