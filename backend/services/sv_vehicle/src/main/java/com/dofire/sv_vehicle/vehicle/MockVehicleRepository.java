package com.dofire.sv_vehicle.vehicle;

import com.dofire.sv_vehicle.global.entitiy.VehicleActionEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MockVehicleRepository implements VehicleRepository {
    private final Map<UUID, VehicleView> vehicles = new LinkedHashMap<>();
    private final List<VehicleActionEntity> actionHistory = new ArrayList<>();

    public MockVehicleRepository() {
        Instant now = Instant.now();
        seed(1, "Toyota", "Corolla", "22000.00", 120, now);
        seed(2, "Toyota", "Camry", "31000.00", 45, now);
        seed(3, "Honda", "Civic", "25000.00", 95, now);
        seed(4, "Honda", "Accord", "33000.00", 15, now);
        seed(5, "Ford", "Focus", "18000.00", 180, now);
        seed(6, "Ford", "Mustang", "45000.00", 90, now);
    }

    private void seed(int number, String make, String model, String price, int days, Instant now) {
        UUID id = new UUID(0, number);
        vehicles.put(id, new VehicleView(id, make, model, VehicleStatus.AVAILABLE,
                new BigDecimal(price), now.minus(days, ChronoUnit.DAYS), 0, null, null));
    }

    @Override
    public synchronized List<VehicleView> findAll() {
        return List.copyOf(vehicles.values());
    }

    @Override
    public synchronized void logAction(UUID vehicleId, long rowVersion, VehicleActionType action,
                                       VehicleActionStatus actionStatus, UUID userId, Instant now) {
        VehicleView vehicle = vehicles.get(vehicleId);
        if (vehicle == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found");
        }
        if (vehicle.rowVersion() != rowVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle has changed; reload and retry");
        }
        actionHistory.add(new VehicleActionEntity(vehicleId, action, actionStatus, userId, now));
        vehicles.put(vehicleId, vehicle.withAction(action, actionStatus));
    }
}
