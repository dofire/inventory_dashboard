package com.dofire.sv_vehicle.api.set_vehicle_action;

import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SetVehicleActionService {
    private static final Logger log = LoggerFactory.getLogger(SetVehicleActionService.class);

    private final SetVehicleActionRepository repository;

    public SetVehicleActionService(SetVehicleActionRepository repository) {
        this.repository = repository;
    }

    public void updateStatus(UpdateStatusRequest request) {
        VehicleActionStatus actionStatus = resolveActionStatus(request);

        log.info(
            "<> Saving a {} proposal with status {}.",
            request.status(),
            actionStatus
        );

        recordAction(
            request,
            actionStatus
        );

        log.info("<> Vehicle proposal saved and action history updated.");
    }

    private VehicleActionStatus resolveActionStatus(UpdateStatusRequest request) {
        return request.actionStatus() == null ? VehicleActionStatus.CREATED : request.actionStatus();
    }

    private void recordAction(
        UpdateStatusRequest request,
        VehicleActionStatus actionStatus
    ) {
        repository.logAction(
            request.vehicleId(),
            request.rowVersion(),
            request.status(),
            actionStatus,
            request.userId(),
            Instant.now()
        );
    }
}
