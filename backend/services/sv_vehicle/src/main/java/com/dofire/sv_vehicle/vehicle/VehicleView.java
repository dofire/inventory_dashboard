package com.dofire.sv_vehicle.vehicle;

import java.time.Instant;
import java.util.UUID;

public record VehicleView(UUID id, String make, String model, VehicleStatus status,
                          Long price, String currency, Instant stockedAt, long rowVersion,
                          VehicleActionType action, VehicleActionStatus actionStatus) {
    public VehicleView withAction(VehicleActionType action, VehicleActionStatus actionStatus) {
        return new VehicleView(id, make, model, status, price, currency, stockedAt, rowVersion + 1,
                action, actionStatus);
    }
}
