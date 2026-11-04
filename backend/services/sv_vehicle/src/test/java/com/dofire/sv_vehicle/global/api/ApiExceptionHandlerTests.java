package com.dofire.sv_vehicle.global.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTests {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"BAD_REQUEST", "UNAUTHORIZED", "NOT_FOUND", "CONFLICT"})
    void expectedStatusIsPreservedWithoutExposingExceptionDetails(HttpStatus status) {
        var response = handler.handle(new ResponseStatusException(status, "private request data"));
        assertEquals(status, response.getStatusCode());
        assertFalse(response.getBody().getData().contains("private"));
    }

    @Test
    void unexpectedFailureBecomesASanitizedServerError() {
        var response = handler.handle(new IllegalStateException("database password=secret"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().getData().contains("secret"));
    }
}
