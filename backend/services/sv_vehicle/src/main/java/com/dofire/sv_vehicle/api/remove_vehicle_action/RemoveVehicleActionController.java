package com.dofire.sv_vehicle.api.remove_vehicle_action;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class RemoveVehicleActionController {
    private final RemoveVehicleActionService service;

    public RemoveVehicleActionController(RemoveVehicleActionService service) {
        this.service = service;
    }

    @PostMapping("/remove-vehicle-action")
    public RemoveVehicleActionResponse remove(@Valid @RequestBody RemoveVehicleActionRequest request) {
        service.remove(request);
        return new RemoveVehicleActionResponse();
    }
}
