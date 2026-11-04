package com.dofire.sv_vehicle.global.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.dofire.sv_vehicle")
public class ApiExceptionHandler {
    public static class FailureResponse extends BaseResponse<String> {
        public FailureResponse(String data) {
            super(data);
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<FailureResponse> handle(Exception exception) {
        int status = exception instanceof ErrorResponse error ? error.getStatusCode().value() : 500;
        String message = status >= 500 ? "The service could not complete the request." : "The request could not be processed.";
        return ResponseEntity.status(status).body(new FailureResponse(message));
    }
}
