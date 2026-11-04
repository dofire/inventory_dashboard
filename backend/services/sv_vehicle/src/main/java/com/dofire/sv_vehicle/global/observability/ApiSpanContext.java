package com.dofire.sv_vehicle.global.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Attach a bounded API operation to child observations, including @Observed steps. */
@Configuration(proxyBeanMethods = false)
public class ApiSpanContext {
    @Bean
    ObservationFilter inheritApiOperation() {
        return context -> {
            Observation.ContextView ancestor = context;
            String apiOperation = null;
            while (ancestor != null) {
                if ("business.logic".equals(ancestor.getName())) {
                    KeyValue operation = ancestor.getLowCardinalityKeyValue("operation");
                    if (operation != null) {
                        apiOperation = operation.getValue();
                    }
                }
                var parent = ancestor.getParentObservation();
                ancestor = parent == null ? null : parent.getContextView();
            }
            if (apiOperation != null) {
                context.addLowCardinalityKeyValue(KeyValue.of("api.operation", apiOperation));
                if (context.getError() != null) {
                    context.addLowCardinalityKeyValue(KeyValue.of("error.type", context.getError().getClass().getSimpleName()));
                    // Keep failures visible without exporting exception messages containing user data.
                    context.setError(new IllegalStateException("Operation failed"));
                }
            }
            return context;
        };
    }
}
