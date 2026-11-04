package com.dofire.sv_vehicle.api.get_vehicles;

import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import com.dofire.sv_vehicle.global.model.VehicleActionType;
import com.dofire.sv_vehicle.global.model.VehicleStatus;
import java.time.Instant;
import java.util.UUID;

public record VehicleView(
    UUID id,
    String dealershipId,
    String make,
    String model,
    VehicleStatus status,
    Long price,
    String currency,
    Instant stockedAt,
    long rowVersion,
    VehicleActionType action,
    VehicleActionStatus actionStatus
) {}
