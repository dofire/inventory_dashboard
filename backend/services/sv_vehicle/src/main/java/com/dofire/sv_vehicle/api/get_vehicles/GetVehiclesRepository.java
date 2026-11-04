package com.dofire.sv_vehicle.api.get_vehicles;

import com.dofire.sv_vehicle.global.mock.MockVehicleStore;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class GetVehiclesRepository {
    private final MockVehicleStore store;

    public GetVehiclesRepository(MockVehicleStore store) {
        this.store = store;
    }

    public List<VehicleView> findByDealership(String dealershipId) {
        return store.findByDealership(dealershipId);
    }
}
