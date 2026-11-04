package com.dofire.sv_vehicle.global.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "com.dofire.sv_vehicle")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(
        MethodParameter returnType,
        Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
        Object body,
        MethodParameter returnType,
        MediaType contentType,
        Class<? extends HttpMessageConverter<?>> converterType,
        ServerHttpRequest request,
        ServerHttpResponse response
    ) {
        if (body instanceof BaseResponse<?> payload) {
            payload.setRequestId(response.getHeaders().getFirst("X-Request-ID"));
            payload.setTraceId(response.getHeaders().getFirst("X-Trace-ID"));
        }
        return body;
    }
}
