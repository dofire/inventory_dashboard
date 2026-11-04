package com.dofire.sv_vehicle.api.get_vehicles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GetVehiclesService {
    private final GetVehiclesRepository repository;

    public GetVehiclesService(GetVehiclesRepository repository) {
        this.repository = repository;
    }

    public List<VehicleView> findVehicles(int page, int size, List<String> makes,
                                          List<String> models, Integer age, Boolean aging) {
        Instant now = Instant.now();
        Instant agingCutoff = now.minus(90, ChronoUnit.DAYS);
        return repository.findAll().stream()
                .filter(v -> matches(makes, v.make()))
                .filter(v -> matches(models, v.model()))
                .filter(v -> age == null || !v.stockedAt().isAfter(now.minus(age, ChronoUnit.DAYS)))
                .filter(v -> aging == null || v.stockedAt().isBefore(agingCutoff) == aging)
                .sorted(Comparator.comparing(VehicleView::stockedAt)
                        .thenComparing(VehicleView::id).reversed())
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

    private boolean matches(List<String> values, String value) {
        return values == null || values.isEmpty() || values.stream().anyMatch(value::equalsIgnoreCase);
    }

}
