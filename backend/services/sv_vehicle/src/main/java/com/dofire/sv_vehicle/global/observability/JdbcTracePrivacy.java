package com.dofire.sv_vehicle.global.observability;

import io.micrometer.observation.ObservationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JdbcTracePrivacy {
    @Bean
    ObservationFilter redactJdbcValues() {
        return context -> {
            if (context.getName().startsWith("jdbc.")) {
                // Preserve operation timing while excluding SQL literals, URLs and returned values.
                var keys = context.getHighCardinalityKeyValues().stream()
                        .map(value -> value.getKey()).toList();
                keys.forEach(context::removeHighCardinalityKeyValue);
            }
            return context;
        };
    }
}
