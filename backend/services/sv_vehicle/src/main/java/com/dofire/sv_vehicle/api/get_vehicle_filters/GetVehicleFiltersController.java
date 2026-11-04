package com.dofire.sv_vehicle.api.get_vehicle_filters;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GetVehicleFiltersController {
    private final GetVehicleFiltersService service;

    public GetVehicleFiltersController(GetVehicleFiltersService service) {
        this.service = service;
    }

    @GetMapping("/vehicle-filters")
    public Map<String, Map<String, List<String>>> filters() {
        return Map.of("makes", service.filters());
    }
}
