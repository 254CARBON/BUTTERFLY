package com.z254.butterfly.cortex.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Aspect for distributed tracing and observability.
 * Automatically traces service methods and LLM calls.
 */
@Aspect
@Component
@Slf4j
public class TracingAspect {

    private final ObservationRegistry observationRegistry;

    public TracingAspect(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    /**
     * Trace all agent execution methods.
     */
    @Around("execution(* com.z254.butterfly.cortex.agent..*.execute*(..)) || " +
            "execution(* com.z254.butterfly.cortex.agent..*.run*(..))")
    public Object traceAgentExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceMethod(joinPoint, "agent");
    }

    /**
     * Trace all LLM provider calls.
     */
    @Around("execution(* com.z254.butterfly.cortex.llm.provider..*.complete*(..)) || " +
            "execution(* com.z254.butterfly.cortex.llm.provider..*.stream*(..)) || " +
            "execution(* com.z254.butterfly.cortex.llm.provider..*.embed*(..))")
    public Object traceLLMCalls(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceMethod(joinPoint, "llm");
    }

    /**
     * Trace all tool executions.
     */
    @Around("execution(* com.z254.butterfly.cortex.tool..*.execute*(..))")
    public Object traceToolExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceMethod(joinPoint, "tool");
    }

    /**
     * Trace all memory operations.
     */
    @Around("execution(* com.z254.butterfly.cortex.memory..*.store*(..)) || " +
            "execution(* com.z254.butterfly.cortex.memory..*.retrieve*(..)) || " +
            "execution(* com.z254.butterfly.cortex.memory..*.search*(..))")
    public Object traceMemoryOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        return traceMethod(joinPoint, "memory");
    }

    private Object traceMethod(ProceedingJoinPoint joinPoint, String category) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        
        String correlationId = MDC.get("correlationId");
        String taskId = MDC.get("taskId");

        Observation observation = Observation.createNotStarted(
                "cortex." + category + "." + signature.getName(),
                observationRegistry
        );

        observation.lowCardinalityKeyValue("class", signature.getDeclaringType().getSimpleName());
        observation.lowCardinalityKeyValue("method", signature.getName());
        observation.lowCardinalityKeyValue("category", category);
        
        if (correlationId != null) {
            observation.highCardinalityKeyValue("correlationId", correlationId);
        }
        if (taskId != null) {
            observation.highCardinalityKeyValue("taskId", taskId);
        }

        return observation.observe(() -> {
            try {
                long startTime = System.currentTimeMillis();
                Object result = joinPoint.proceed();
                long duration = System.currentTimeMillis() - startTime;
                
                log.debug("Traced {}: {}ms", methodName, duration);
                return result;
            } catch (Throwable e) {
                log.error("Error in traced method {}: {}", methodName, e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
}
