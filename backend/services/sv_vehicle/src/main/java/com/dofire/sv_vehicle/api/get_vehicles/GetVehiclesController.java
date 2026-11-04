package com.dofire.sv_vehicle.api.get_vehicles;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class GetVehiclesController {
    private final GetVehiclesService service;

    public GetVehiclesController(GetVehiclesService service) {
        this.service = service;
    }

    @GetMapping("/vehicles")
    public GetVehiclesResponse vehicles(@Valid @ModelAttribute GetVehiclesRequest request) {
        return new GetVehiclesResponse(service.findVehicles(
            request.getDealershipId(),
            request.getPage(),
            request.getSize(),
            request.getMake(),
            request.getModel(),
            request.getAge(),
            request.getAging(),
            request.getSortKey(),
            request.getSortOrder()
        ));
    }
}
