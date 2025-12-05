package com.z254.butterfly.cortex.llm.guardrails;

import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.llm.LLMResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Output validator for LLM responses.
 * Provides guardrails including PII detection, content filtering, and safety checks.
 */
@Component
@Slf4j
public class OutputValidator {

    private final CortexProperties.SafetyProperties.GuardrailsProperties config;
    
    // PII patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?1[-.]?)?\\(?[0-9]{3}\\)?[-.]?[0-9]{3}[-.]?[0-9]{4}\\b");
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b[0-9]{3}[-]?[0-9]{2}[-]?[0-9]{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:[0-9]{4}[-\\s]?){3}[0-9]{4}\\b");
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
            "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b");
    
    // Unsafe content patterns
    private static final List<Pattern> UNSAFE_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(execute|run|eval)\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)\\bsudo\\b"),
            Pattern.compile("(?i)\\brm\\s+-rf\\b"),
            Pattern.compile("(?i)\\bdrop\\s+table\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)\\bdelete\\s+from\\b", Pattern.CASE_INSENSITIVE)
    );

    public OutputValidator(CortexProperties cortexProperties) {
        this.config = cortexProperties.getSafety().getGuardrails();
    }

    /**
     * Validate an LLM response.
     *
     * @param response the LLM response to validate
     * @return validation result
     */
    public Mono<ValidationResult> validate(LLMResponse response) {
        return Mono.fromCallable(() -> {
            List<ValidationIssue> issues = new ArrayList<>();
            String content = response.getContent();
            
            if (content == null || content.isEmpty()) {
                return ValidationResult.valid();
            }
            
            // PII detection
            if (config.isPiiDetectionEnabled()) {
                issues.addAll(detectPII(content));
            }
            
            // Content filtering
            if (config.isContentFilteringEnabled()) {
                issues.addAll(filterUnsafeContent(content));
            }
            
            // Custom blocked patterns
            if (config.getBlockedPatterns() != null && !config.getBlockedPatterns().isEmpty()) {
                issues.addAll(checkBlockedPatterns(content, config.getBlockedPatterns()));
            }
            
            if (issues.isEmpty()) {
                return ValidationResult.valid();
            } else {
                log.warn("Output validation found {} issues", issues.size());
                return ValidationResult.invalid(issues);
            }
        });
    }

    /**
     * Validate and optionally redact sensitive content.
     *
     * @param response the LLM response
     * @return response with redacted content if needed
     */
    public Mono<LLMResponse> validateAndRedact(LLMResponse response) {
        return validate(response)
                .map(result -> {
                    if (result.isValid()) {
                        return response;
                    }
                    
                    String content = response.getContent();
                    if (content == null) {
                        return response;
                    }
                    
                    // Redact PII
                    String redacted = redactPII(content);
                    
                    return LLMResponse.builder()
                            .id(response.getId())
                            .model(response.getModel())
                            .providerId(response.getProviderId())
                            .content(redacted)
                            .toolCalls(response.getToolCalls())
                            .finishReason(response.getFinishReason())
                            .usage(response.getUsage())
                            .latencyMs(response.getLatencyMs())
                            .metadata(response.getMetadata())
                            .build();
                });
    }

    /**
     * Check if content is safe for external tool execution.
     *
     * @param content the content to check
     * @return true if safe
     */
    public boolean isSafeForToolExecution(String content) {
        if (content == null || content.isEmpty()) {
            return true;
        }
        
        // Check for dangerous commands
        for (Pattern pattern : UNSAFE_PATTERNS) {
            if (pattern.matcher(content).find()) {
                log.warn("Unsafe content detected for tool execution: {}", pattern.pattern());
                return false;
            }
        }
        
        return true;
    }

    private List<ValidationIssue> detectPII(String content) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        if (EMAIL_PATTERN.matcher(content).find()) {
            issues.add(new ValidationIssue(IssueType.PII_DETECTED, "Email address detected"));
        }
        if (PHONE_PATTERN.matcher(content).find()) {
            issues.add(new ValidationIssue(IssueType.PII_DETECTED, "Phone number detected"));
        }
        if (SSN_PATTERN.matcher(content).find()) {
            issues.add(new ValidationIssue(IssueType.PII_DETECTED, "SSN-like pattern detected"));
        }
        if (CREDIT_CARD_PATTERN.matcher(content).find()) {
            issues.add(new ValidationIssue(IssueType.PII_DETECTED, "Credit card number detected"));
        }
        
        return issues;
    }

    private List<ValidationIssue> filterUnsafeContent(String content) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        for (Pattern pattern : UNSAFE_PATTERNS) {
            if (pattern.matcher(content).find()) {
                issues.add(new ValidationIssue(
                        IssueType.UNSAFE_CONTENT, 
                        "Potentially unsafe content: " + pattern.pattern()));
            }
        }
        
        return issues;
    }

    private List<ValidationIssue> checkBlockedPatterns(String content, Set<String> patterns) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        for (String pattern : patterns) {
            try {
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(content).find()) {
                    issues.add(new ValidationIssue(
                            IssueType.BLOCKED_PATTERN, 
                            "Blocked pattern matched: " + pattern));
                }
            } catch (Exception e) {
                log.warn("Invalid blocked pattern: {}", pattern);
            }
        }
        
        return issues;
    }

    /**
     * Redact PII from content.
     */
    public String redactPII(String content) {
        if (content == null) return null;
        
        String redacted = content;
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[EMAIL REDACTED]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[PHONE REDACTED]");
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("[SSN REDACTED]");
        redacted = CREDIT_CARD_PATTERN.matcher(redacted).replaceAll("[CARD REDACTED]");
        
        return redacted;
    }

    /**
     * Validation result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private List<ValidationIssue> issues;
        
        public static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }
        
        public static ValidationResult invalid(List<ValidationIssue> issues) {
            return new ValidationResult(false, issues);
        }
    }

    /**
     * Validation issue.
     */
    @Data
    @AllArgsConstructor
    public static class ValidationIssue {
        private IssueType type;
        private String message;
    }

    /**
     * Types of validation issues.
     */
    public enum IssueType {
        PII_DETECTED,
        UNSAFE_CONTENT,
        BLOCKED_PATTERN,
        CONTENT_TOO_LONG,
        MALFORMED_OUTPUT
    }
}
