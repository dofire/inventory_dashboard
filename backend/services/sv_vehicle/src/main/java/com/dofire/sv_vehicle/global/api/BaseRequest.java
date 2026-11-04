package com.dofire.sv_vehicle.global.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseRequest {
    @NotBlank
    @Pattern(regexp = "DEALER_HCM_001|DEALER_HN_002")
    private String dealershipId;
}
