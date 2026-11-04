package com.dofire.sv_vehicle.global.observability;

import com.dofire.sv_vehicle.api.set_vehicle_action.UpdateStatusRequest;
import com.dofire.sv_vehicle.global.model.VehicleActionType;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.validation.BeanPropertyBindingResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TracingWebConfigurationTests {
    @Test
    void validationAndSerializationAreChildrenOfTheRequestAndRestoreItsScope() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        List<Observation.Context> completed = new ArrayList<>();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            public boolean supportsContext(Observation.Context context) { return true; }
            public void onStop(Observation.Context context) { completed.add(context); }
        });
        TracingWebConfiguration config = new TracingWebConfiguration(registry);
        Observation request = Observation.start("http.server.requests", registry);
        try (Observation.Scope scope = request.openScope(); var validator = config.tracingValidator()) {
            validator.afterPropertiesSet();
            var payload = new UpdateStatusRequest();
            payload.setRowVersion(0L);
            payload.setStatus(VehicleActionType.MAINTENANCE_PLANNED);
            var errors = new BeanPropertyBindingResult(payload, "request");
            validator.validate(payload, errors);
            assertTrue(errors.hasFieldErrors("userId"));
            assertSame(request, registry.getCurrentObservation());

            List<HttpMessageConverter<?>> converters = new ArrayList<>();
            converters.add(new StringHttpMessageConverter());
            config.traceMessageConverters(converters);
            @SuppressWarnings("unchecked")
            HttpMessageConverter<String> converter = (HttpMessageConverter<String>) converters.getFirst();
            MockHttpOutputMessage output = new MockHttpOutputMessage();
            converter.write("response", MediaType.TEXT_PLAIN, output);
            assertEquals("response", output.getBodyAsString());
            assertSame(request, registry.getCurrentObservation());
        } finally {
            request.stop();
        }
        for (String name : List.of("request.validation", "response.mapping")) {
            var child = completed.stream().filter(context -> name.equals(context.getName())).findFirst().orElseThrow();
            assertSame(request, child.getParentObservation());
        }
        assertNull(registry.getCurrentObservation());
    }

    @Test
    void serviceAndRepositoryObservationsNestWithoutChangingResults() {
        ObservationRegistry registry = ObservationRegistry.create();
        List<Observation.Context> completed = new ArrayList<>();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            public boolean supportsContext(Observation.Context context) { return true; }
            public void onStop(Observation.Context context) { completed.add(context); }
        });
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class);
        org.mockito.Mockito.when(jdbc.query(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<com.dofire.sv_vehicle.api.get_vehicles.VehicleView>>any()
        )).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(jdbc.queryForObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.namedparam.SqlParameterSource.class),
                org.mockito.ArgumentMatchers.eq(Long.class)
        )).thenReturn(0L);
        var repositoryFactory = new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(
                new com.dofire.sv_vehicle.api.get_vehicles.GetVehiclesRepository(jdbc));
        repositoryFactory.addAspect(new OperationTracing(registry));
        com.dofire.sv_vehicle.api.get_vehicles.GetVehiclesRepository repository = repositoryFactory.getProxy();
        var serviceFactory = new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(
                new com.dofire.sv_vehicle.api.get_vehicles.GetVehiclesService(repository, registry));
        serviceFactory.addAspect(new OperationTracing(registry));
        com.dofire.sv_vehicle.api.get_vehicles.GetVehiclesService service = serviceFactory.getProxy();
        Observation request = Observation.start("http.server.requests", registry);
        try (Observation.Scope scope = request.openScope()) {
            assertEquals(0, service.findVehicles("DEALER_HCM_001", 0, 10, null, null, null, null, "stockedAt", "desc").items().size());
            assertSame(request, registry.getCurrentObservation());
        } finally {
            request.stop();
        }
        var business = completed.stream().filter(c -> c.getName().equals("business.logic")).findFirst().orElseThrow();
        var storage = completed.stream().filter(c -> c.getName().equals("repository.database")).findFirst().orElseThrow();
        assertSame(request, business.getParentObservation());
        assertEquals("vehicles.filter-and-sort", storage.getParentObservation().getContextView().getName());
        assertSame(business, storage.getParentObservation().getContextView().getParentObservation().getContextView());
    }

    @Test
    void jdbcObservationExcludesRawQueryAndParameters() {
        var context = new Observation.Context();
        context.setName("jdbc.query");
        context.addHighCardinalityKeyValue(io.micrometer.common.KeyValue.of("jdbc.query[0]", "select 'secret'"));
        new JdbcTracePrivacy().redactJdbcValues().map(context);
        assertEquals(0, context.getHighCardinalityKeyValues().stream().count());
    }
}
