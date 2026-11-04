package com.dofire.sv_vehicle.global.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class AuthControllerTests {
    private final AuthController controller = new AuthController();

    @ParameterizedTest
    @CsvSource({"' TONY ',DEALER_HCM_001,tony", "John,DEALER_HN_002,john"})
    void mockAccountIsNormalizedAndTheSelectedDealershipIsReturned(
        String account,
        String dealership,
        String expectedAccount
    ) {
        var request = new AuthController.LoginRequest();
        request.setAccount(account);
        request.setDealershipId(dealership);
        var profile = controller.login(request).getData();
        assertEquals(expectedAccount, profile.account());
        assertEquals(dealership, profile.dealershipId());
        assertNotNull(profile.userId());
        assertFalse(profile.email().isBlank());
    }

    @Test
    void unknownAccountIsUnauthorized() {
        var request = new AuthController.LoginRequest();
        request.setAccount("unknown");
        request.setDealershipId("DEALER_HCM_001");
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> controller.login(request)).getStatusCode());
    }
}
