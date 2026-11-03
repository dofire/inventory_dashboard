package com.dofire.sv_vehicle.vehicle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class VehicleController {
    private final VehicleService service;

    public VehicleController(VehicleService service) {
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

    @PostMapping("/update-vehicle-status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@Valid @RequestBody UpdateStatusRequest request) {
        service.updateStatus(request);
    }

    @GetMapping("/vehicle-filters")
    public Map<String, Map<String, List<String>>> filters() {
        return Map.of("makes", service.filters());
    }

}
