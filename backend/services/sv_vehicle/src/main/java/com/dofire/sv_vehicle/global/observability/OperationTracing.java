package com.dofire.sv_vehicle.global.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;

/** Scopes observations around endpoint layers without recording arguments or results. */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@Component
public class OperationTracing {
    private final ObservationRegistry registry;

    public OperationTracing(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Around("execution(public * com.dofire.sv_vehicle.api..*Service.*(..))")
    public Object business(ProceedingJoinPoint call) throws Throwable {
        return observe("business.logic", call);
    }

    @Around("execution(public * com.dofire.sv_vehicle.api..*Repository.*(..))")
    public Object repository(ProceedingJoinPoint call) throws Throwable {
        return observe("repository.database", call);
    }

    private Object observe(String name, ProceedingJoinPoint call) throws Throwable {
        Observation observation = Observation.createNotStarted(name, registry)
                .lowCardinalityKeyValue("operation", call.getSignature().getDeclaringType().getSimpleName()
                        + "." + call.getSignature().getName()).start();
        try (Observation.Scope scope = observation.openScope()) {
            return call.proceed();
        } catch (Throwable error) {
            // Exception messages may contain rejected values or SQL; record only the class.
            observation.lowCardinalityKeyValue("error.type", error.getClass().getSimpleName());
            throw error;
        } finally {
            observation.stop();
        }
    }
}
