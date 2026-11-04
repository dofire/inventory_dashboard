package com.dofire.sv_vehicle.api.get_vehicle_filters;

import java.util.List;
import java.util.Map;
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
        var options = repository.findByDealership(dealershipId);

        log.info(
            "<> Make and model filters are ready: {} makes and {} model options.",
            options.size(),
            options.values().stream().mapToInt(List::size).sum()
        );

        return options;
    }

}
