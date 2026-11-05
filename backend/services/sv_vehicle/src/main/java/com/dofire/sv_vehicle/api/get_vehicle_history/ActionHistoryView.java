package com.dofire.sv_vehicle.api.get_vehicle_history;

import com.dofire.sv_vehicle.global.model.VehicleActionType;
import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import java.time.Instant;
import java.util.UUID;

public record ActionHistoryView(
    UUID id,
    VehicleActionType action,
    VehicleActionStatus actionStatus,
    boolean removed,
    long rowVersion,
    Instant createdAt,
    UUID createdBy
) {}
