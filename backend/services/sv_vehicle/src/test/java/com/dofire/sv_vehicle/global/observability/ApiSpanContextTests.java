package com.dofire.sv_vehicle.global.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.observation.aop.ObservedAspect;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiSpanContextTests {
    public static class Task {
        @Observed(name = "taskA")
        public String execute() { return "done"; }
    }

    @Test
    void annotationAndLambdaStepsInheritApiAndRestoreScopeOnFailure() {
        ObservationRegistry registry = ObservationRegistry.create();
        List<Observation.Context> completed = new ArrayList<>();
        registry.observationConfig().observationFilter(new ApiSpanContext().inheritApiOperation());
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            public boolean supportsContext(Observation.Context context) { return true; }
            public void onStop(Observation.Context context) { completed.add(context); }
        });
        AspectJProxyFactory factory = new AspectJProxyFactory(new Task());
        factory.addAspect(new ObservedAspect(registry));
        Task task = factory.getProxy();
        Observation api = Observation.createNotStarted("business.logic", registry)
                .lowCardinalityKeyValue("operation", "GetVehiclesService.findVehicles").start();
        try (Observation.Scope scope = api.openScope()) {
            assertEquals("done", Observation.createNotStarted("taskB", registry).observe(task::execute));
            assertSame(api, registry.getCurrentObservation());
            RuntimeException failure = new RuntimeException("failure");
            assertSame(failure, assertThrows(RuntimeException.class,
                    () -> Observation.createNotStarted("failed-step", registry).observe((Runnable) () -> { throw failure; })));
            assertSame(api, registry.getCurrentObservation());
        } finally {
            api.stop();
        }
        var taskA = completed.stream().filter(c -> c.getName().equals("taskA")).findFirst().orElseThrow();
        var taskB = completed.stream().filter(c -> "taskB".equals(c.getName())).findFirst().orElseThrow();
        assertSame(taskB, taskA.getParentObservation().getContextView());
        assertSame(api, taskB.getParentObservation());
        for (var context : completed) {
            assertEquals("GetVehiclesService.findVehicles", context.getLowCardinalityKeyValue("api.operation").getValue());
        }
        assertNull(registry.getCurrentObservation());
    }
}
