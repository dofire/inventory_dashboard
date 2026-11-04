package com.dofire.sv_vehicle.api.get_vehicles;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GetVehiclesController {
    private final GetVehiclesService service;

    public GetVehiclesController(GetVehiclesService service) {
        this.service = service;
    }

    @GetMapping("/vehicles")
    public List<VehicleView> vehicles(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) List<String> make,
            @RequestParam(required = false) List<String> model,
            @RequestParam(required = false) @Min(0) Integer age,
            @RequestParam(required = false) Boolean aging) {
        return service.findVehicles(page, size, make, model, age, aging);
    }
}
