package com.dofire.sv_vehicle.api.get_vehicle_filters;

import com.dofire.sv_vehicle.api.get_vehicles.VehicleView;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GetVehicleFiltersService {
    private final GetVehicleFiltersRepository repository;

    public GetVehicleFiltersService(GetVehicleFiltersRepository repository) {
        this.repository = repository;
    }

    public Map<String, List<String>> filters() {
        return repository.findAll().stream().collect(Collectors.groupingBy(
                VehicleView::make, TreeMap::new,
                Collectors.collectingAndThen(Collectors.mapping(VehicleView::model, Collectors.toList()),
                        models -> models.stream().distinct().sorted().toList())));
    }

}
