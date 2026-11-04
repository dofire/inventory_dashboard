package com.dofire.sv_vehicle.global.mock;

import com.dofire.sv_vehicle.api.get_vehicles.VehicleView;
import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import com.dofire.sv_vehicle.global.model.VehicleActionType;
import com.dofire.sv_vehicle.global.model.VehicleStatus;

import com.dofire.sv_vehicle.global.entitiy.VehicleActionEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MockVehicleStore {
    private final Map<UUID, VehicleView> vehicles = new LinkedHashMap<>();
    private final List<VehicleActionEntity> actionHistory = new ArrayList<>();

    public MockVehicleStore() {
        Instant now = Instant.now();
        seed(
            1,
            "DEALER_HCM_001",
            "Toyota",
            "Corolla",
            2200000L,
            120,
            now
        );
        seed(
            2,
            "DEALER_HCM_001",
            "Toyota",
            "Camry",
            3100000L,
            45,
            now
        );
        seed(
            3,
            "DEALER_HCM_001",
            "Honda",
            "Civic",
            2500000L,
            95,
            now
        );
        seed(
            4,
            "DEALER_HCM_001",
            "Honda",
            "Accord",
            3300000L,
            15,
            now
        );
        seed(
            5,
            "DEALER_HCM_001",
            "Ford",
            "Focus",
            1800000L,
            180,
            now
        );
        seed(
            6,
            "DEALER_HCM_001",
            "Ford",
            "Mustang",
            4500000L,
            90,
            now
        );
        seed(
            7,
            "DEALER_HN_002",
            "Mazda",
            "CX-5",
            2900000L,
            110,
            now
        );
        seed(
            8,
            "DEALER_HN_002",
            "Hyundai",
            "Tucson",
            2800000L,
            30,
            now
        );
        seed(
            9,
            "DEALER_HN_002",
            "Kia",
            "Sportage",
            2700000L,
            150,
            now
        );
    }

    private void seed(
        int number,
        String dealershipId,
        String make,
        String model,
        long price,
        int days,
        Instant now
    ) {
        UUID id = new UUID(0, number);
        vehicles.put(
            id,
            new VehicleView(
                id,
                dealershipId,
                make,
                model,
                VehicleStatus.AVAILABLE,
                price,
                "USD",
                now.minus(
                    days,
                    ChronoUnit.DAYS
                ),
                0,
                null,
                null
            )
        );
    }

    public synchronized List<VehicleView> findByDealership(String dealershipId) {
        return vehicles.values().stream()
                .filter(vehicle -> vehicle.dealershipId().equals(dealershipId))
                .toList();
    }

    public synchronized void logAction(
        String dealershipId,
        UUID vehicleId,
        long rowVersion,
        VehicleActionType action,
        VehicleActionStatus actionStatus,
        UUID userId,
        Instant now
    ) {
        VehicleView vehicle = vehicles.get(vehicleId);
        if (vehicle == null || !vehicle.dealershipId().equals(dealershipId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found");
        }
        if (vehicle.rowVersion() != rowVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle has changed; reload and retry");
        }
        actionHistory.add(new VehicleActionEntity(
            vehicleId,
            action,
            actionStatus,
            userId,
            now
        ));
        vehicles.put(
            vehicleId,
            vehicle.withAction(
                action,
                actionStatus
            )
        );
    }
}
