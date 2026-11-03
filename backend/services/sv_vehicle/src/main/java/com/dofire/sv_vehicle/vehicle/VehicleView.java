package com.dofire.sv_vehicle.vehicle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VehicleView(UUID id, String make, String model, VehicleStatus status,
                          BigDecimal price, Instant stockedAt, long rowVersion,
                          VehicleActionType action, VehicleActionStatus actionStatus) {
    public VehicleView withAction(VehicleActionType action, VehicleActionStatus actionStatus) {
        return new VehicleView(id, make, model, status, price, stockedAt, rowVersion + 1,
                action, actionStatus);
    }
}
