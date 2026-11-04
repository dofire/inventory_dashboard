package com.dofire.sv_vehicle.global.mock;

import com.dofire.sv_vehicle.api.get_vehicle_filters.GetVehicleFiltersRepository;
import com.dofire.sv_vehicle.api.get_vehicle_filters.GetVehicleFiltersService;
import com.dofire.sv_vehicle.api.get_vehicles.GetVehiclesRepository;
import com.dofire.sv_vehicle.api.get_vehicles.GetVehiclesService;
import com.dofire.sv_vehicle.api.get_vehicles.GetVehiclesRequest;
import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import com.dofire.sv_vehicle.global.model.VehicleActionType;
import io.micrometer.observation.ObservationRegistry;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class DealershipIsolationTests {
    private static final String HCM = "DEALER_HCM_001";
    private static final String HANOI = "DEALER_HN_002";

    @Test
    void inventoryAndFilterOptionsBelongToTheSelectedDealership() {
        var store = new MockVehicleStore();
        var service = new GetVehiclesService(new GetVehiclesRepository(store), ObservationRegistry.create());
        var hcm = service.findVehicles(HCM, 0, 20, null, null, null, null, "stockedAt", "desc");
        var hanoi = service.findVehicles(HANOI, 0, 20, null, null, null, null, "stockedAt", "desc");
        assertEquals(6, hcm.size());
        assertEquals(3, hanoi.size());
        assertTrue(hcm.stream().allMatch(vehicle -> HCM.equals(vehicle.dealershipId())));
        assertTrue(hanoi.stream().allMatch(vehicle -> HANOI.equals(vehicle.dealershipId())));
        assertTrue(hcm.stream().noneMatch(vehicle -> hanoi.stream().anyMatch(other -> other.id().equals(vehicle.id()))));
        assertTrue(service.findVehicles(HANOI, 0, 20, java.util.List.of("Toyota"), null, null, null, "price", "asc").isEmpty());
        var filters = new GetVehicleFiltersService(new GetVehicleFiltersRepository(store));
        assertTrue(filters.filters(HCM).containsKey("Toyota"));
        assertFalse(filters.filters(HANOI).containsKey("Toyota"));
        assertTrue(filters.filters(HANOI).containsKey("Mazda"));
    }

    @Test
    void anotherDealershipCannotChangeAVehicleOrItsVersion() {
        var store = new MockVehicleStore();
        var vehicle = store.findByDealership(HCM).getFirst();
        var userId = UUID.randomUUID();
        var error = assertThrows(ResponseStatusException.class, () -> store.logAction(
            HANOI,
            vehicle.id(),
            vehicle.rowVersion(),
            VehicleActionType.MAINTENANCE_PLANNED,
            VehicleActionStatus.CREATED,
            userId,
            Instant.now()
        ));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals(vehicle, store.findByDealership(HCM).getFirst());
        var hanoiBefore = store.findByDealership(HANOI);
        store.logAction(
            HCM,
            vehicle.id(),
            vehicle.rowVersion(),
            VehicleActionType.MAINTENANCE_PLANNED,
            VehicleActionStatus.CREATED,
            userId,
            Instant.now()
        );
        assertEquals(1, store.findByDealership(HCM).getFirst().rowVersion());
        assertEquals(hanoiBefore, store.findByDealership(HANOI));
    }

    @Test
    void dealershipIsRequiredAndMustBeSupported() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new GetVehiclesRequest();
            assertFalse(validator.validate(request).isEmpty());
            request.setDealershipId("UNKNOWN");
            assertFalse(validator.validate(request).isEmpty());
            request.setDealershipId(HANOI);
            assertTrue(validator.validate(request).isEmpty());
        }
    }
}
