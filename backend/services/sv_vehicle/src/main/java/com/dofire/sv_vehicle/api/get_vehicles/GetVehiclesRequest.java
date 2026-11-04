package com.dofire.sv_vehicle.api.get_vehicles;

import com.dofire.sv_vehicle.global.api.BaseRequest;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetVehiclesRequest extends BaseRequest {
    @Min(0)
    private int page = 0;
    @Min(1)
    @Max(100)
    private int size = 20;
    private List<String> make;
    private List<String> model;
    @Min(0)
    private Integer age;
    private Boolean aging;
    @NotNull
    @Pattern(regexp = "make|model|price|status|stockedAt|daysInStock|action")
    private String sortKey = "stockedAt";
    @NotNull
    @Pattern(regexp = "asc|desc", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String sortOrder = "desc";
}
