package com.dofire.sv_vehicle.api.get_vehicle_filters;

import com.dofire.sv_vehicle.api.get_vehicles.VehicleView;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GetVehicleFiltersService {
    private static final Logger log = LoggerFactory.getLogger(GetVehicleFiltersService.class);

    private final GetVehicleFiltersRepository repository;

    public GetVehicleFiltersService(GetVehicleFiltersRepository repository) {
        this.repository = repository;
    }

    public Map<String, List<String>> filters(String dealershipId) {
        var options = buildFilterOptions(repository.findByDealership(dealershipId));

        log.info(
            "<> Make and model filters are ready: {} makes and {} model options.",
            options.size(),
            options.values().stream().mapToInt(List::size).sum()
        );

        return options;
    }

    private Map<String, List<String>> buildFilterOptions(List<VehicleView> vehicles) {
        return vehicles.stream().collect(Collectors.groupingBy(
            VehicleView::make,
            TreeMap::new,
            Collectors.collectingAndThen(
                Collectors.mapping(
                    VehicleView::model,
                    Collectors.toList()
                ),
                this::sortedUniqueModels
            )
        ));
    }

    private List<String> sortedUniqueModels(List<String> models) {
        return models.stream().distinct().sorted().toList();
    }
}
