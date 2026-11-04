package com.dofire.sv_vehicle.global.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseResponse<T> {
    private String requestId;
    private String traceId;
    private final T data;

    protected BaseResponse(T data) {
        this.data = data;
    }
}
