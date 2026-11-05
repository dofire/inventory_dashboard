package com.dofire.sv_vehicle.api.get_vehicle_history;

import com.dofire.sv_vehicle.global.api.Pagination;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class GetVehicleHistoryService {
    private final GetVehicleHistoryRepository repository;

    public GetVehicleHistoryService(GetVehicleHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GetVehicleHistoryResponse history(
        UUID vehicleId,
        GetVehicleHistoryRequest request
    ) {
        long total = repository.count(vehicleId, request.getDealershipId());
        var items = repository.findHistory(vehicleId, request.getDealershipId(), request.getPage(), request.getSize());
        return new GetVehicleHistoryResponse(
            items,
            new Pagination(request.getPage(), request.getSize(), total, total / request.getSize() + (total % request.getSize() == 0 ? 0 : 1))
        );
    }
}
