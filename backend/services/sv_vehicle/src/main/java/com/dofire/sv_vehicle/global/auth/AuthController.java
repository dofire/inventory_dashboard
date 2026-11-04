package com.dofire.sv_vehicle.global.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
public class AuthController {
    public record LoginRequest(@NotBlank String account) {}
    public record Profile(UUID userId, String accountId, String account, String name, int age, String email) {}

    private static final Map<String, Profile> ACCOUNTS = Map.of(
            "tony", new Profile(UUID.fromString("11111111-1111-4111-8111-111111111111"), "account-tony", "tony", "Tony Nguyen", 32, "tony@example.com"),
            "john", new Profile(UUID.fromString("22222222-2222-4222-8222-222222222222"), "account-john", "john", "John Smith", 28, "john@example.com")
    );

    @PostMapping("/login")
    public Profile login(@Valid @RequestBody LoginRequest request) {
        Profile profile = ACCOUNTS.get(request.account().trim().toLowerCase(Locale.ROOT));
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown mock account");
        }
        return profile;
    }
}
