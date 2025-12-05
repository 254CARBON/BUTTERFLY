package com.z254.butterfly.aurora.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Aspect for distributed tracing across AURORA service operations.
 * <p>
 * Automatically creates spans for:
 * <ul>
 *     <li>RCA analysis operations</li>
 *     <li>Remediation execution</li>
 *     <li>Client calls (PLATO, SYNAPSE, CAPSULE)</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class TracingAspect {

    private final Tracer tracer;

    public TracingAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    @Pointcut("execution(* com.z254.butterfly.aurora.rca..*(..))")
    public void rcaOperations() {}

    @Pointcut("execution(* com.z254.butterfly.aurora.remediation..*(..))")
    public void remediationOperations() {}

    @Pointcut("execution(* com.z254.butterfly.aurora.client..*(..))")
    public void clientOperations() {}

    @Pointcut("execution(* com.z254.butterfly.aurora.stream..*(..))")
    public void streamOperations() {}

    /**
     * Trace RCA operations.
     */
    @Around("rcaOperations()")
    public Object traceRcaOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceOperation(joinPoint, "aurora.rca");
    }

    /**
     * Trace remediation operations.
     */
    @Around("remediationOperations()")
    public Object traceRemediationOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceOperation(joinPoint, "aurora.remediation");
    }

    /**
     * Trace client operations.
     */
    @Around("clientOperations()")
    public Object traceClientOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceOperation(joinPoint, "aurora.client");
    }

    /**
     * Trace stream operations.
     */
    @Around("streamOperations()")
    public Object traceStreamOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceOperation(joinPoint, "aurora.stream");
    }

    private Object traceOperation(ProceedingJoinPoint joinPoint, String prefix) throws Throwable {
        String spanName = prefix + "." + joinPoint.getSignature().getName();
        Span span = tracer.nextSpan().name(spanName);
        
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            // Add span context to MDC
            MDC.put(AuroraStructuredLogger.MDC_TRACE_ID, span.context().traceId());
            MDC.put(AuroraStructuredLogger.MDC_SPAN_ID, span.context().spanId());
            
            // Add method info to span
            span.tag("class", joinPoint.getSignature().getDeclaringType().getSimpleName());
            span.tag("method", joinPoint.getSignature().getName());
            
            return joinPoint.proceed();
        } catch (Throwable t) {
            span.error(t);
            throw t;
        } finally {
            span.end();
            MDC.remove(AuroraStructuredLogger.MDC_TRACE_ID);
            MDC.remove(AuroraStructuredLogger.MDC_SPAN_ID);
        }
    }
}
