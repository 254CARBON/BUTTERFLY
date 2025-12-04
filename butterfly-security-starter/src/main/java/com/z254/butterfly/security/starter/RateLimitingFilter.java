package com.z254.butterfly.security.starter;

import com.z254.butterfly.common.security.ButterflyPrincipal;
import com.z254.butterfly.common.security.SecurityContextPropagator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting filter for BUTTERFLY services.
 * <p>
 * Supports:
 * <ul>
 *   <li>Per-user rate limiting (based on principal subject)</li>
 *   <li>Global rate limiting (based on IP address)</li>
 *   <li>Configurable window size and request limits</li>
 * </ul>
 * <p>
 * For production, this should be backed by Redis for distributed rate limiting.
 * This in-memory implementation is suitable for single-instance deployments.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final int maxRequestsPerWindow;
    private final Duration windowSize;
    private final boolean perUser;

    // Simple in-memory rate limit store (use Redis in production)
    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(int maxRequestsPerMinute, Duration windowSize, boolean perUser) {
        this.maxRequestsPerWindow = maxRequestsPerMinute;
        this.windowSize = windowSize;
        this.perUser = perUser;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String key = getRateLimitKey(request);
        RateLimitBucket bucket = buckets.computeIfAbsent(key, k -> new RateLimitBucket(windowSize, maxRequestsPerWindow));

        // Check if request is allowed
        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for key: {}", key);
            
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(bucket.getSecondsUntilReset()));
            response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequestsPerWindow));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(bucket.getResetTime().getEpochSecond()));
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests\"}");
            response.setContentType("application/json");
            return;
        }

        // Add rate limit headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequestsPerWindow));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(bucket.getResetTime().getEpochSecond()));

        filterChain.doFilter(request, response);
    }

    private String getRateLimitKey(HttpServletRequest request) {
        if (perUser) {
            // Try to get principal subject
            Optional<ButterflyPrincipal> principal = SecurityContextPropagator.getCurrentPrincipal();
            if (principal.isPresent() && !ButterflyPrincipal.DEFAULT_TENANT.equals(principal.get().getSubject())) {
                return "user:" + principal.get().getTenantId() + ":" + principal.get().getSubject();
            }
        }
        
        // Fall back to IP-based limiting
        String ip = getClientIp(request);
        return "ip:" + ip;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * Simple sliding window rate limit bucket.
     */
    private static class RateLimitBucket {
        private final Duration windowSize;
        private final int maxRequests;
        private final AtomicInteger count;
        private volatile Instant windowStart;

        RateLimitBucket(Duration windowSize, int maxRequests) {
            this.windowSize = windowSize;
            this.maxRequests = maxRequests;
            this.count = new AtomicInteger(0);
            this.windowStart = Instant.now();
        }

        synchronized boolean tryConsume() {
            Instant now = Instant.now();
            
            // Check if window has expired
            if (now.isAfter(windowStart.plus(windowSize))) {
                // Reset window
                windowStart = now;
                count.set(1);
                return true;
            }

            // Try to consume
            int current = count.incrementAndGet();
            return current <= maxRequests;
        }

        int getRemaining() {
            return Math.max(0, maxRequests - count.get());
        }

        Instant getResetTime() {
            return windowStart.plus(windowSize);
        }

        long getSecondsUntilReset() {
            return Math.max(0, Duration.between(Instant.now(), getResetTime()).getSeconds());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip rate limiting for health checks
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || 
               path.startsWith("/actuator/info") ||
               path.startsWith("/actuator/prometheus");
    }
}

