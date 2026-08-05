package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.dto.AuthResponse;
import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.dto.RegisterRequest;
import com.featureflag.auth_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public String register(
            @Valid @RequestBody RegisterRequest request) {

        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @RequestBody LoginRequest request) {

        return authService.login(request);
    }

    @GetMapping("/profile")
    public String profile() {
        return "Welcome to Protected Profile";
    }
}