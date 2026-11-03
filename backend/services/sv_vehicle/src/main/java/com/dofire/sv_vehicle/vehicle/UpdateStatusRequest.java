package com.dofire.sv_vehicle.vehicle;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateStatusRequest(@NotNull UUID userId, @NotNull UUID vehicleId,
                                  @NotNull @Min(0) Long rowVersion,
                                  @NotNull VehicleActionType status,
                                  VehicleActionStatus actionStatus) {}
