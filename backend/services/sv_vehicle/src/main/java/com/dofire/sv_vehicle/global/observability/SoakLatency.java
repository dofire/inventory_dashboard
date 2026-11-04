package com.dofire.sv_vehicle.global.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.ThreadLocalRandom;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Opt-in local soak delays, nested inside business.logic (or the login HTTP span). */
@Aspect
@Component
@Profile("soak")
@ConditionalOnProperty(name = "app.soak.delays-enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class SoakLatency {
    private final ObservationRegistry observations;
    private final double probability;

    public SoakLatency(
        ObservationRegistry observations,
        @Value("${app.soak.delay-probability:0.08}") double probability
    ) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Soak delay probability must be between 0 and 1");
        }
        this.observations = observations;
        this.probability = probability;
    }

    @Around("execution(public * com.dofire.sv_vehicle.api..*Service.*(..)) || "
            + "execution(public * com.dofire.sv_vehicle.global.auth.AuthController.login(..))")
    public Object simulateLatency(ProceedingJoinPoint call) throws Throwable {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes request
                && "true".equals(request.getRequest().getHeader("X-Soak-Test"))
                && ThreadLocalRandom.current().nextDouble() < probability) {
            Observation.createNotStarted(
                "soak.simulated-delay",
                observations
            ).lowCardinalityKeyValue(
                "api.operation",
                call.getSignature().getDeclaringType().getSimpleName() + "." + call.getSignature().getName()
            ).observe(this::pause);
        }
        return call.proceed();
    }

    private void pause() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(100, 501));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Soak delay interrupted");
        }
    }
}
