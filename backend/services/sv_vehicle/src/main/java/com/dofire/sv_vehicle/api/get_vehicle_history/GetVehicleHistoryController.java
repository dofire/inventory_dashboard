package com.dofire.sv_vehicle.api.get_vehicle_history;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class GetVehicleHistoryController {
    private final GetVehicleHistoryService service;

    public GetVehicleHistoryController(GetVehicleHistoryService service) {
        this.service = service;
    }

    @GetMapping("/vehicles/{vehicleId}/actions")
    public GetVehicleHistoryResponse history(
        @PathVariable UUID vehicleId,
        @Valid @ModelAttribute GetVehicleHistoryRequest request
    ) {
        return service.history(vehicleId, request);
    }
}
