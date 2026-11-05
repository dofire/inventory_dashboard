package com.dofire.sv_vehicle.api.get_vehicle_history;

import com.dofire.sv_vehicle.global.api.BaseRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetVehicleHistoryRequest extends BaseRequest {
    @Min(0)
    private int page = 0;
    @Min(1)
    @Max(100)
    private int size = 20;
}
