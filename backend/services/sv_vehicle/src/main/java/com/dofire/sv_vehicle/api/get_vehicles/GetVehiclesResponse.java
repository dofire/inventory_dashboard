package com.dofire.sv_vehicle.api.get_vehicles;

import com.dofire.sv_vehicle.global.api.BaseResponse;
import com.dofire.sv_vehicle.global.api.Pagination;
import java.util.List;
import lombok.Getter;

@Getter
public class GetVehiclesResponse extends BaseResponse<List<VehicleView>> {
    private final Pagination pagination;

    public GetVehiclesResponse(VehiclePage page) {
        super(page.items());
        this.pagination = new Pagination(
            page.page(),
            page.size(),
            page.totalItems(),
            page.totalItems() / page.size() + (page.totalItems() % page.size() == 0 ? 0 : 1)
        );
    }
}
