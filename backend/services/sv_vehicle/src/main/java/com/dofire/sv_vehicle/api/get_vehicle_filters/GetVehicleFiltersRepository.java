package com.dofire.sv_vehicle.api.get_vehicle_filters;

import com.dofire.sv_vehicle.api.get_vehicles.VehicleView;
import com.dofire.sv_vehicle.global.mock.MockVehicleStore;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class GetVehicleFiltersRepository {
    private final MockVehicleStore store;

    public GetVehicleFiltersRepository(MockVehicleStore store) {
        this.store = store;
    }

    public List<VehicleView> findAll() {
        return store.findAll();
    }
}
