package com.dofire.sv_vehicle.api.remove_vehicle_action;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RemoveVehicleActionService {
    private static final Logger log = LoggerFactory.getLogger(RemoveVehicleActionService.class);
    private final RemoveVehicleActionRepository repository;

    public RemoveVehicleActionService(RemoveVehicleActionRepository repository) {
        this.repository = repository;
    }

    public void remove(RemoveVehicleActionRequest request) {
        repository.remove(request);
        log.info("<> Removed the current vehicle proposal and preserved its history.");
    }
}
