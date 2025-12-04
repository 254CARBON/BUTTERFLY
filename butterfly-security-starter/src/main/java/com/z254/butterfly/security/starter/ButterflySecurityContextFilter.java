package com.z254.butterfly.security.starter;

import com.z254.butterfly.common.security.ButterflyPrincipal;
import com.z254.butterfly.common.security.SecurityContextPropagator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filter that extracts BUTTERFLY security context from HTTP headers.
 * <p>
 * Propagates:
 * <ul>
 *   <li>ButterflyPrincipal from X-Butterfly-* headers</li>
 *   <li>Governance correlation ID from X-Governance-Correlation-Id</li>
 *   <li>MDC context for logging</li>
 * </ul>
 */
public class ButterflySecurityContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ButterflySecurityContextFilter.class);

    private final String serviceName;

    public ButterflySecurityContextFilter(String serviceName) {
        this.serviceName = serviceName != null ? serviceName : "unknown";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Extract headers
            Map<String, String> headers = extractHeaders(request);

            // Extract and set principal from headers (if not already set by JWT)
            ButterflyPrincipal principal = SecurityContextPropagator.getCurrentPrincipal()
                    .orElseGet(() -> SecurityContextPropagator.fromHttpHeaders(headers));
            SecurityContextPropagator.setCurrentPrincipal(principal);

            // Handle correlation ID
            String correlationId = headers.get(SecurityContextPropagator.HEADER_CORRELATION_ID);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            SecurityContextPropagator.setCorrelationId(correlationId);

            // Handle causation ID
            String causationId = headers.get(SecurityContextPropagator.HEADER_CAUSATION_ID);
            if (causationId != null && !causationId.isBlank()) {
                SecurityContextPropagator.setCausationId(causationId);
            }

            // Set MDC context for logging
            MDC.put("service", serviceName);
            MDC.put("correlationId", correlationId);
            MDC.put("tenantId", principal.getTenantId());
            MDC.put("principal", principal.getSubject());
            MDC.put("requestId", request.getHeader("X-Request-Id"));
            MDC.put("method", request.getMethod());
            MDC.put("path", request.getRequestURI());

            // Add correlation ID to response headers
            response.setHeader(SecurityContextPropagator.HEADER_CORRELATION_ID, correlationId);

            if (log.isDebugEnabled()) {
                log.debug("Security context established: principal={}, tenant={}, correlation={}",
                        principal.getSubject(), principal.getTenantId(), correlationId);
            }

            filterChain.doFilter(request, response);

        } finally {
            // Clean up thread-local context
            SecurityContextPropagator.clearCurrentPrincipal();
            MDC.clear();
        }
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}

