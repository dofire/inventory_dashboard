package com.dofire.sv_vehicle.api.get_vehicle_history;

import com.dofire.sv_vehicle.global.api.BaseResponse;
import com.dofire.sv_vehicle.global.api.Pagination;
import java.util.List;
import lombok.Getter;

@Getter
public class GetVehicleHistoryResponse extends BaseResponse<List<ActionHistoryView>> {
    private final Pagination pagination;

    public GetVehicleHistoryResponse(
        List<ActionHistoryView> items,
        Pagination pagination
    ) {
        super(items);
        this.pagination = pagination;
    }
}
