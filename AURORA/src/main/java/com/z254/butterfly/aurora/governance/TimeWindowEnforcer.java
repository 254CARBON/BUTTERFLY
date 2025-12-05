package com.z254.butterfly.aurora.governance;

import com.z254.butterfly.aurora.config.AuroraProperties;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;

/**
 * Enforces time-based constraints for remediations.
 * <p>
 * Handles:
 * <ul>
 *     <li>Maintenance window validation</li>
 *     <li>Business hours restrictions</li>
 *     <li>Freeze period enforcement</li>
 *     <li>Emergency override handling</li>
 * </ul>
 */
@Slf4j
@Component
public class TimeWindowEnforcer {

    private final AuroraProperties auroraProperties;
    
    // Configured maintenance windows (simplified)
    private final List<MaintenanceWindow> maintenanceWindows = new ArrayList<>();
    
    // Freeze periods (e.g., during high-traffic events)
    private final List<FreezePeriod> freezePeriods = new ArrayList<>();

    public TimeWindowEnforcer(AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;
        initializeDefaultWindows();
    }

    /**
     * Check if current time is within acceptable window for remediation.
     */
    public TimeWindowResult checkTimeWindow() {
        return checkTimeWindow(Instant.now());
    }

    /**
     * Check if a specific time is within acceptable window.
     */
    public TimeWindowResult checkTimeWindow(Instant timestamp) {
        ZonedDateTime zonedTime = timestamp.atZone(ZoneId.of("UTC"));
        
        // Check freeze periods first
        Optional<FreezePeriod> activeFreeze = getActiveFreezePeriod(timestamp);
        if (activeFreeze.isPresent()) {
            return TimeWindowResult.builder()
                    .allowed(false)
                    .inMaintenanceWindow(false)
                    .inFreezePeriod(true)
                    .reason("In freeze period: " + activeFreeze.get().getReason())
                    .freezePeriod(activeFreeze.get())
                    .nextAllowedTime(activeFreeze.get().getEnd())
                    .build();
        }

        // Check maintenance window
        Optional<MaintenanceWindow> activeMaintenance = getActiveMaintenanceWindow(timestamp);
        boolean inMaintenance = activeMaintenance.isPresent();

        // Check business hours
        boolean inBusinessHours = isBusinessHours(zonedTime);

        // Determine if allowed
        boolean allowed = true;
        String reason = "Within acceptable time window";

        // Low-risk can run anytime, high-risk prefers maintenance windows
        if (!inMaintenance && !inBusinessHours) {
            // Off-hours, non-maintenance - allow with caution
            reason = "Off-hours, non-maintenance window - proceed with monitoring";
        } else if (inMaintenance) {
            reason = "Within maintenance window - optimal for changes";
        } else if (inBusinessHours) {
            reason = "Business hours - ensure monitoring is active";
        }

        return TimeWindowResult.builder()
                .allowed(allowed)
                .inMaintenanceWindow(inMaintenance)
                .inFreezePeriod(false)
                .inBusinessHours(inBusinessHours)
                .reason(reason)
                .maintenanceWindow(activeMaintenance.orElse(null))
                .build();
    }

    /**
     * Check if remediation should be deferred.
     */
    public DeferralRecommendation shouldDefer(double riskScore) {
        TimeWindowResult windowResult = checkTimeWindow();
        
        if (windowResult.isInFreezePeriod()) {
            return DeferralRecommendation.builder()
                    .shouldDefer(true)
                    .reason(windowResult.getReason())
                    .deferUntil(windowResult.getNextAllowedTime())
                    .build();
        }

        // High-risk actions should prefer maintenance windows
        if (riskScore > 0.7 && !windowResult.isInMaintenanceWindow()) {
            Optional<MaintenanceWindow> nextWindow = getNextMaintenanceWindow();
            if (nextWindow.isPresent()) {
                // Only suggest deferral if window is within reasonable time
                if (nextWindow.get().getStart().isBefore(Instant.now().plus(Duration.ofHours(4)))) {
                    return DeferralRecommendation.builder()
                            .shouldDefer(true)
                            .reason("High-risk action recommended during maintenance window")
                            .deferUntil(nextWindow.get().getStart())
                            .canOverride(true)
                            .build();
                }
            }
        }

        return DeferralRecommendation.builder()
                .shouldDefer(false)
                .reason("No deferral needed")
                .build();
    }

    /**
     * Register an emergency override (bypass time constraints).
     */
    public EmergencyOverride registerEmergencyOverride(String remediationId,
                                                        String justification,
                                                        String approvedBy) {
        log.warn("Emergency override registered: remediationId={}, approvedBy={}",
                remediationId, approvedBy);

        return EmergencyOverride.builder()
                .remediationId(remediationId)
                .justification(justification)
                .approvedBy(approvedBy)
                .approvedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(1)))
                .build();
    }

    /**
     * Add a freeze period.
     */
    public void addFreezePeriod(FreezePeriod freezePeriod) {
        freezePeriods.add(freezePeriod);
        log.info("Added freeze period: {} - {} ({})",
                freezePeriod.getStart(), freezePeriod.getEnd(), freezePeriod.getReason());
    }

    /**
     * Add a maintenance window.
     */
    public void addMaintenanceWindow(MaintenanceWindow window) {
        maintenanceWindows.add(window);
        log.info("Added maintenance window: {} - {}",
                window.getStart(), window.getEnd());
    }

    // ========== Private Methods ==========

    private void initializeDefaultWindows() {
        // Add default weekly maintenance windows (Sundays 2-6 AM UTC)
        // In production, these would come from configuration
        for (int week = 0; week < 52; week++) {
            LocalDate sunday = LocalDate.now()
                    .with(DayOfWeek.SUNDAY)
                    .plusWeeks(week);
            
            Instant start = sunday.atTime(2, 0).toInstant(ZoneOffset.UTC);
            Instant end = sunday.atTime(6, 0).toInstant(ZoneOffset.UTC);
            
            maintenanceWindows.add(MaintenanceWindow.builder()
                    .id("weekly-maintenance-" + week)
                    .start(start)
                    .end(end)
                    .recurring(true)
                    .description("Weekly maintenance window")
                    .build());
        }
    }

    private Optional<FreezePeriod> getActiveFreezePeriod(Instant timestamp) {
        return freezePeriods.stream()
                .filter(fp -> !timestamp.isBefore(fp.getStart()) && timestamp.isBefore(fp.getEnd()))
                .findFirst();
    }

    private Optional<MaintenanceWindow> getActiveMaintenanceWindow(Instant timestamp) {
        return maintenanceWindows.stream()
                .filter(mw -> !timestamp.isBefore(mw.getStart()) && timestamp.isBefore(mw.getEnd()))
                .findFirst();
    }

    private Optional<MaintenanceWindow> getNextMaintenanceWindow() {
        Instant now = Instant.now();
        return maintenanceWindows.stream()
                .filter(mw -> mw.getStart().isAfter(now))
                .min(Comparator.comparing(MaintenanceWindow::getStart));
    }

    private boolean isBusinessHours(ZonedDateTime time) {
        DayOfWeek day = time.getDayOfWeek();
        int hour = time.getHour();
        
        // Business hours: Monday-Friday, 9 AM - 6 PM
        boolean weekday = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
        boolean duringHours = hour >= 9 && hour < 18;
        
        return weekday && duringHours;
    }

    // ========== Data Classes ==========

    @Data
    @Builder
    public static class TimeWindowResult {
        private boolean allowed;
        private boolean inMaintenanceWindow;
        private boolean inFreezePeriod;
        private boolean inBusinessHours;
        private String reason;
        private MaintenanceWindow maintenanceWindow;
        private FreezePeriod freezePeriod;
        private Instant nextAllowedTime;
    }

    @Data
    @Builder
    public static class MaintenanceWindow {
        private String id;
        private Instant start;
        private Instant end;
        private boolean recurring;
        private String description;
    }

    @Data
    @Builder
    public static class FreezePeriod {
        private String id;
        private Instant start;
        private Instant end;
        private String reason;
        private String createdBy;
    }

    @Data
    @Builder
    public static class DeferralRecommendation {
        private boolean shouldDefer;
        private String reason;
        private Instant deferUntil;
        private boolean canOverride;
    }

    @Data
    @Builder
    public static class EmergencyOverride {
        private String remediationId;
        private String justification;
        private String approvedBy;
        private Instant approvedAt;
        private Instant expiresAt;
    }
}
