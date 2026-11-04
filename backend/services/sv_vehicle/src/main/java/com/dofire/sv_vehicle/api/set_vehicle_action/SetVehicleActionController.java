package com.dofire.sv_vehicle.api.set_vehicle_action;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SetVehicleActionController {
    private final SetVehicleActionService service;

    public SetVehicleActionController(SetVehicleActionService service) {
        this.service = service;
    }

    @PostMapping("/update-vehicle-status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@Valid @RequestBody UpdateStatusRequest request) {
        service.updateStatus(request);
    }
}
