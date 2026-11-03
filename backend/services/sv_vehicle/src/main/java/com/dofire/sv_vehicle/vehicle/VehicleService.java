package com.dofire.sv_vehicle.vehicle;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class VehicleService {
    private final VehicleRepository repository;

    public VehicleService(VehicleRepository repository) {
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

    public Map<String, List<String>> filters() {
        return repository.findAll().stream().collect(Collectors.groupingBy(
                VehicleView::make, TreeMap::new,
                Collectors.collectingAndThen(Collectors.mapping(VehicleView::model, Collectors.toList()),
                        models -> models.stream().distinct().sorted().toList())));
    }

    public void updateStatus(UpdateStatusRequest request) {
        VehicleActionStatus actionStatus = request.actionStatus() == null
                ? VehicleActionStatus.CREATED : request.actionStatus();
        repository.logAction(request.vehicleId(), request.rowVersion(), request.status(),
                actionStatus, request.userId(), Instant.now());
    }
}
