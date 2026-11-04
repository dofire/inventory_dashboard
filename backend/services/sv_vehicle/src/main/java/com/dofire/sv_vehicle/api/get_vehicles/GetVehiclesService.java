package com.dofire.sv_vehicle.api.get_vehicles;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public List<VehicleView> findVehicles(
        int page,
        int size,
        List<String> makes,
        List<String> models,
        Integer age,
        Boolean aging
    ) {
        Instant now = Instant.now();

        var vehicles = repository.findAll();

        return Observation.createNotStarted(
            "vehicles.filter-and-sort",
            observations
        ).observe(() -> filterAndSort(
            vehicles,
            page,
            size,
            makes,
            models,
            age,
            aging,
            now
        ));
    }

    private List<VehicleView> filterAndSort(
        List<VehicleView> vehicles,
        int page,
        int size,
        List<String> makes,
        List<String> models,
        Integer age,
        Boolean aging,
        Instant now
    ) {
        log.info(
            "<> Checking {} vehicles against the inventory filters.",
            vehicles.size()
        );

        var results = vehicles.stream()
                .filter(vehicle -> matches(
                    makes,
                    vehicle.make()
                ))
                .filter(vehicle -> matches(
                    models,
                    vehicle.model()
                ))
                .filter(vehicle -> matchesStockAge(
                    vehicle,
                    age,
                    aging,
                    now
                ))
                .sorted(Comparator.comparing(VehicleView::stockedAt)
                        .thenComparing(VehicleView::id).reversed())
                .skip((long) page * size)
                .limit(size)
                .toList();

        log.info(
            "<> Found {} vehicles for page {} (up to {} per page).",
            results.size(),
            page + 1,
            size
        );

        return results;
    }

    private boolean matchesStockAge(
        VehicleView vehicle,
        Integer age,
        Boolean aging,
        Instant now
    ) {
        boolean meetsMinimumAge = age == null
                || !vehicle.stockedAt().isAfter(now.minus(
                    age,
                    ChronoUnit.DAYS
                ));

        boolean isAging = vehicle.stockedAt().isBefore(now.minus(
            90,
            ChronoUnit.DAYS
        ));

        return meetsMinimumAge && (aging == null || isAging == aging);
    }

    private boolean matches(
        List<String> values,
        String value
    ) {
        return values == null || values.isEmpty() || values.stream().anyMatch(value::equalsIgnoreCase);
    }
}
