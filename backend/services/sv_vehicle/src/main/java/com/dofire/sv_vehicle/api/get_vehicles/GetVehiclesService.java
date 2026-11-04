package com.dofire.sv_vehicle.api.get_vehicles;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetVehiclesService {
    private static final Logger log = LoggerFactory.getLogger(GetVehiclesService.class);
    private final GetVehiclesRepository repository;
    private final ObservationRegistry observations;

    public GetVehiclesService(
        GetVehiclesRepository repository,
        ObservationRegistry observations
    ) {
        this.repository = repository;
        this.observations = observations;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public VehiclePage findVehicles(
        String dealershipId,
        int page,
        int size,
        List<String> makes,
        List<String> models,
        Integer age,
        Boolean aging,
        String sortKey,
        String sortOrder
    ) {
        Instant now = Instant.now();

        var vehicles = Observation.createNotStarted(
            "vehicles.filter-and-sort",
            observations
        ).observe(() -> repository.findVehicles(
            dealershipId,
            page,
            size,
            makes,
            models,
            age,
            aging,
            sortKey,
            sortOrder,
            now
        ));

        log.info(
            "<> Loaded {} vehicles from PostgreSQL for page {} (up to {} per page).",
            vehicles.size(),
            page + 1,
            size
        );
        long totalItems = repository.countVehicles(
            dealershipId,
            makes,
            models,
            age,
            aging,
            now
        );
        return new VehiclePage(
            vehicles,
            totalItems,
            page,
            size
        );
    }
}
