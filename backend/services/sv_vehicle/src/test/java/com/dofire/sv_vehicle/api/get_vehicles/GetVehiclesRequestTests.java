package com.dofire.sv_vehicle.api.get_vehicles;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GetVehiclesRequestTests {
    private static final String HANOI = "DEALER_HN_002";

    @Test
    void dealershipIsRequiredAndMustBeSupported() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new GetVehiclesRequest();
            assertFalse(validator.validate(request).isEmpty());
            request.setDealershipId("UNKNOWN");
            assertFalse(validator.validate(request).isEmpty());
            request.setDealershipId(HANOI);
            assertTrue(validator.validate(request).isEmpty());
        }
    }
}
