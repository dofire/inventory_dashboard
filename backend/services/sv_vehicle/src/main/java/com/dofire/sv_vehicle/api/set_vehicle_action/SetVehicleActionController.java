package com.dofire.sv_vehicle.api.set_vehicle_action;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SetVehicleActionController {
    private final SetVehicleActionService service;

    public SetVehicleActionController(SetVehicleActionService service) {
        this.service = service;
    }

    @PostMapping("/update-vehicle-status")
    public UpdateStatusResponse updateStatus(@Valid @RequestBody UpdateStatusRequest request) {
        service.updateStatus(request);
        return new UpdateStatusResponse(null);
    }
}
