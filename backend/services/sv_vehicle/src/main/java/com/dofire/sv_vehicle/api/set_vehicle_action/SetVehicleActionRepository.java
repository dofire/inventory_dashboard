package com.dofire.sv_vehicle.api.set_vehicle_action;

import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import com.dofire.sv_vehicle.global.model.VehicleActionType;
import com.dofire.sv_vehicle.global.mock.MockVehicleStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class SetVehicleActionRepository {
    private final MockVehicleStore store;

    public SetVehicleActionRepository(MockVehicleStore store) {
        this.store = store;
    }

    public void logAction(
        String dealershipId,
        UUID vehicleId,
        long rowVersion,
        VehicleActionType action,
        VehicleActionStatus actionStatus,
        UUID userId,
        Instant now
    ) {
        store.logAction(
            dealershipId,
            vehicleId,
            rowVersion,
            action,
            actionStatus,
            userId,
            now
        );
    }
}
