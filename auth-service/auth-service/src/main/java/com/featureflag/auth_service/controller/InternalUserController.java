package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.dto.UserAccountStatusResponse;
import com.featureflag.auth_service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Internal User Account Status APIs")
public class InternalUserController {

    private final AuthService authService;

    @Operation(summary = "Get authoritative current user account status by email and userId")
    @GetMapping("/status")
    public UserAccountStatusResponse getUserAccountStatus(
            @RequestParam @NotBlank String email,
            @RequestParam @NotNull Long userId
    ) {
        return authService.getUserAccountStatus(email, userId);
    }
}
