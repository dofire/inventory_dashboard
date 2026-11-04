package com.dofire.sv_vehicle.api.get_vehicle_filters;

import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GetVehicleFiltersController {
    private final GetVehicleFiltersService service;

    public GetVehicleFiltersController(GetVehicleFiltersService service) {
        this.service = service;
    }

    @GetMapping("/vehicle-filters")
    public GetVehicleFiltersResponse filters(@Valid @ModelAttribute GetVehicleFiltersRequest request) {
        return new GetVehicleFiltersResponse(Map.of("makes", service.filters(request.getDealershipId())));
    }
}
