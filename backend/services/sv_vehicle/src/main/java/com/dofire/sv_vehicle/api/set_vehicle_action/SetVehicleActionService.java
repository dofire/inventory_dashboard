package com.dofire.sv_vehicle.api.set_vehicle_action;

import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SetVehicleActionService {
    private final SetVehicleActionRepository repository;

    public SetVehicleActionService(SetVehicleActionRepository repository) {
        this.repository = repository;
    }

    public void updateStatus(UpdateStatusRequest request) {
        VehicleActionStatus actionStatus = request.actionStatus() == null
                ? VehicleActionStatus.CREATED : request.actionStatus();
        repository.logAction(request.vehicleId(), request.rowVersion(), request.status(),
                actionStatus, request.userId(), Instant.now());
    }
}
