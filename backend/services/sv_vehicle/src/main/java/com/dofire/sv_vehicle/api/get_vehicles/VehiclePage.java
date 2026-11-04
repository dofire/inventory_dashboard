package com.dofire.sv_vehicle.api.get_vehicles;

import java.util.List;

public record VehiclePage(
    List<VehicleView> items,
    long totalItems,
    int page,
    int size
) {}
