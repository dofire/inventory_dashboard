package com.dofire.sv_vehicle.api.get_vehicles;

import com.dofire.sv_vehicle.global.api.BaseResponse;
import java.util.List;

public class GetVehiclesResponse extends BaseResponse<List<VehicleView>> {
    public GetVehiclesResponse(List<VehicleView> data) {
        super(data);
    }
}
