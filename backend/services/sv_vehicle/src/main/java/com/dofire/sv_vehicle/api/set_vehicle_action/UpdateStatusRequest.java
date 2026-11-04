package com.dofire.sv_vehicle.api.set_vehicle_action;

import com.dofire.sv_vehicle.global.model.VehicleActionStatus;
import com.dofire.sv_vehicle.global.model.VehicleActionType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateStatusRequest(@NotNull UUID userId, @NotNull UUID vehicleId,
                                  @NotNull @Min(0) Long rowVersion,
                                  @NotNull VehicleActionType status,
                                  VehicleActionStatus actionStatus) {}
