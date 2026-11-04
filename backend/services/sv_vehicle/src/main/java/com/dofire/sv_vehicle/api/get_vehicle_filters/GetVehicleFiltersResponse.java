package com.dofire.sv_vehicle.api.get_vehicle_filters;

import com.dofire.sv_vehicle.global.api.BaseResponse;
import java.util.List;
import java.util.Map;

public class GetVehicleFiltersResponse extends BaseResponse<Map<String, Map<String, List<String>>>> {
    public GetVehicleFiltersResponse(Map<String, Map<String, List<String>>> data) {
        super(data);
    }
}
