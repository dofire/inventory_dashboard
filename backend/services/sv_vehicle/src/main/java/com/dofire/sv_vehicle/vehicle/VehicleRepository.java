package com.dofire.sv_vehicle.vehicle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VehicleRepository {
    List<VehicleView> findAll();

    void logAction(UUID vehicleId, long rowVersion, VehicleActionType action,
                   VehicleActionStatus actionStatus, UUID userId, Instant now);
}
