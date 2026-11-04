package com.dofire.sv_vehicle.global.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SoakLatencyTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(ObservationRegistry.class, ObservationRegistry::create)
            .withUserConfiguration(SoakLatency.class);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void bothProfileAndEnableFlagAreRequired() {
        context.run(application -> assertTrue(application.getBeansOfType(SoakLatency.class).isEmpty()));
        context.withPropertyValues("spring.profiles.active=soak")
                .run(application -> assertTrue(application.getBeansOfType(SoakLatency.class).isEmpty()));
        context.withPropertyValues("app.soak.delays-enabled=true")
                .run(application -> assertTrue(application.getBeansOfType(SoakLatency.class).isEmpty()));
        context.withPropertyValues("spring.profiles.active=soak", "app.soak.delays-enabled=true")
                .run(application -> assertNotNull(application.getBean(SoakLatency.class)));
    }

    @Test
    void headerIsRequiredAndSimulatedDelayIsAChildOfBusinessLogic() throws Throwable {
        var registry = ObservationRegistry.create();
        List<Observation.Context> completed = new ArrayList<>();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            public boolean supportsContext(Observation.Context context) { return true; }
            public void onStop(Observation.Context context) { completed.add(context); }
        });
        var latency = new SoakLatency(registry, 1);
        var request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var call = mock(ProceedingJoinPoint.class);
        var signature = mock(Signature.class);
        when(call.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(SoakLatencyTests.class);
        when(signature.getName()).thenReturn("testOperation");
        when(call.proceed()).thenReturn("result");
        var parent = Observation.start("business.logic", registry);
        try (var scope = parent.openScope()) {
            assertEquals("result", latency.simulateLatency(call));
            assertTrue(completed.isEmpty());
            request.addHeader("X-Soak-Test", "true");
            assertEquals("result", latency.simulateLatency(call));
            assertEquals(1, completed.size());
            assertEquals("soak.simulated-delay", completed.getFirst().getName());
            assertSame(parent, completed.getFirst().getParentObservation());
            assertSame(parent, registry.getCurrentObservation());
        } finally {
            parent.stop();
        }
        verify(call, times(2)).proceed();
        assertNull(registry.getCurrentObservation());
    }
}
