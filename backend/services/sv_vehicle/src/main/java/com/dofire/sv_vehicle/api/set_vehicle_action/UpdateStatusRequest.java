package com.dofire.sv_vehicle.api.set_vehicle_action;

import com.dofire.sv_vehicle.global.api.BaseRequest;
import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import com.dofire.sv_vehicle.global.model.VehicleActionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStatusRequest extends BaseRequest {
    @NotNull
    private UUID userId;
    @NotNull
    private UUID vehicleId;
    @NotNull
    @Min(0)
    private Long rowVersion;
    @NotNull
    private VehicleActionType status;
    private VehicleActionStatus actionStatus;
}
