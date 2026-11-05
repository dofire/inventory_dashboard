package com.dofire.sv_vehicle.global.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Demo-friendly lifecycle logs for traced observations, without arguments or exception messages. */
@Component
@ConditionalOnProperty(name = "app.tracing.span-logs-enabled", havingValue = "true", matchIfMissing = true)
public class SpanLoggingHandler implements ObservationHandler<Observation.Context> {
    private static final Logger log = LoggerFactory.getLogger(SpanLoggingHandler.class);
    private static final java.util.Map<String, String> TASKS = java.util.Map.ofEntries(
            java.util.Map.entry("GetVehiclesService.findVehicles", "vehicle inventory search"),
            java.util.Map.entry("GetVehiclesRepository.findVehicles", "loading dealership vehicles from PostgreSQL"),
            java.util.Map.entry("GetVehicleFiltersService.filters", "building make and model filter options"),
            java.util.Map.entry("GetVehicleFiltersRepository.findByDealership", "loading dealership make and model options from PostgreSQL"),
            java.util.Map.entry("SetVehicleActionService.updateStatus", "saving the vehicle action proposal"),
            java.util.Map.entry("SetVehicleActionRepository.logAction", "recording the proposal in PostgreSQL"),
            java.util.Map.entry("vehicles.filter-and-sort", "filtering and sorting inventory results"),
            java.util.Map.entry("soak.simulated-delay", "simulating a brief delay for the soak test"),
            java.util.Map.entry("GetVehicleHistoryService.history", "loading the vehicle action history"),
            java.util.Map.entry("GetVehicleHistoryRepository.findHistory", "reading action history from PostgreSQL"),
            java.util.Map.entry("GetVehicleHistoryRepository.count", "counting vehicle history entries"),
            java.util.Map.entry("RemoveVehicleActionService.remove", "removing the current vehicle proposal"),
            java.util.Map.entry("RemoveVehicleActionRepository.remove", "recording the proposal removal in PostgreSQL"),
            java.util.Map.entry("request.validation", "checking the request values"),
            java.util.Map.entry("response.mapping", "preparing the response body"),
            java.util.Map.entry("http.server.requests", "handling the HTTP request")
    );
    private static final Object START_LOGGED = new Object();

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    @Override
    public void onScopeOpened(Observation.Context context) {
        if (context.get(START_LOGGED) == null && span(context) != null) {
            context.put(START_LOGGED, true);
            write(context, "started");
        }
    }

    @Override
    public void onStop(Observation.Context context) {
        if (span(context) != null) {
            write(context, context.getError() == null
                    && context.getLowCardinalityKeyValue("error.type") == null ? "completed" : "failed");
        }
    }

    private Span span(Observation.Context context) {
        TracingContext tracing = context.get(TracingContext.class);
        return tracing == null ? null : tracing.getSpan();
    }

    private void write(Observation.Context context, String outcome) {
        Span span = span(context);
        // Explicit IDs keep onStop correlated even if the tracing handler already restored the parent scope.
        var previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("traceId", span.context().traceId());
            MDC.put("spanId", span.context().spanId());
            String name = context.getContextualName() == null ? context.getName() : context.getContextualName();
            var operation = context.getLowCardinalityKeyValue("operation");
            String operationName = operation == null ? name : operation.getValue();
            String task = TASKS.getOrDefault(operationName,
                    TASKS.getOrDefault(context.getName(), name));
            String message = switch (outcome) {
                case "started" -> "Starting " + task + ".";
                case "failed" -> "Could not complete " + task + ".";
                default -> "Finished " + task + ".";
            };
            log.atInfo().addKeyValue("spanName", name)
                    .addKeyValue("operation", operationName)
                    .addKeyValue("outcome", outcome).log("<> {}", message);
        } finally {
            if (previous == null) MDC.clear();
            else MDC.setContextMap(previous);
        }
    }
}
