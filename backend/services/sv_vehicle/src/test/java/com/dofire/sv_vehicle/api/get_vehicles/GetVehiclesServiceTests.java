package com.dofire.sv_vehicle.api.get_vehicles;

import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GetVehiclesServiceTests {
    private final GetVehiclesRepository repository = mock(GetVehiclesRepository.class);
    private final ObservationRegistry observations = ObservationRegistry.create();
    private final GetVehiclesService service = new GetVehiclesService(repository, observations);

    @Test
    void pageAndCountShareDealershipFiltersAndTheSameAgeCutoff() {
        var makes = List.of("Toyota");
        var models = List.of("Corolla");
        when(repository.findVehicles(anyString(), anyInt(), anyInt(), any(), any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(List.of());
        when(repository.countVehicles(anyString(), any(), any(), any(), any(), any())).thenReturn(22L);

        var result = service.findVehicles("DEALER_HCM_001", 3, 10, makes, models, 90, true, "price", "asc");

        var pageCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findVehicles(eq("DEALER_HCM_001"), eq(3), eq(10), eq(makes), eq(models), eq(90), eq(true), eq("price"), eq("asc"), pageCutoff.capture());
        verify(repository).countVehicles("DEALER_HCM_001", makes, models, 90, true, pageCutoff.getValue());
        assertTrue(result.items().isEmpty());
        assertEquals(22, result.totalItems());
        assertEquals(3, result.page());
        assertEquals(10, result.size());
        assertNull(observations.getCurrentObservation());
    }

    @Test
    void failedPageQueryPropagatesAndDoesNotLeaveAnObservationScopeOpen() {
        var failure = new IllegalStateException("database unavailable");
        when(repository.findVehicles(anyString(), anyInt(), anyInt(), any(), any(), any(), any(), anyString(), anyString(), any()))
                .thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.findVehicles(
            "DEALER_HN_002", 0, 20, null, null, null, null, "stockedAt", "desc"
        )));
        verify(repository, never()).countVehicles(anyString(), any(), any(), any(), any(), any());
        assertNull(observations.getCurrentObservation());
    }
}
