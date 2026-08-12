package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.dto.AuthResponse;
import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.dto.RegisterRequest;
import com.featureflag.auth_service.dto.TokenValidationResponse;
import com.featureflag.auth_service.security.JwtService;
import com.featureflag.auth_service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication APIs")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public String register(
            @Valid @RequestBody RegisterRequest request) {

        return authService.register(request);
    }

    @Operation(summary = "Login and get JWT token")
    @PostMapping("/login")
    public AuthResponse login(
            @RequestBody LoginRequest request) {

        return authService.login(request);
    }

    @Operation(summary = "Access protected profile")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/profile")
    public String profile() {
        return "Welcome to Protected Profile";
    }

    @Operation(summary = "Validate JWT token")
    @GetMapping("/validate")
    public TokenValidationResponse validateToken(
            @RequestParam String token) {

        return authService.validateToken(token);
    }

    @Operation(summary = "Get active notification recipients by role")
    @GetMapping("/recipients")
    public List<String> getNotificationRecipients(
            @RequestParam(required = false) List<String> roles) {

        return authService.getNotificationRecipients(roles);
    }
}