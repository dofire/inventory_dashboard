package com.dofire.sv_vehicle.api.remove_vehicle_action;

import com.dofire.sv_vehicle.global.api.BaseRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RemoveVehicleActionRequest extends BaseRequest {
    @NotNull
    private UUID vehicleId;
    @NotNull
    private UUID userId;
    @NotNull
    @Min(0)
    private Long rowVersion;
}
