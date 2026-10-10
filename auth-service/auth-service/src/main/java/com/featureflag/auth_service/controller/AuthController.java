package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.dto.AuthResponse;
import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.dto.ProfileResponse;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.List;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication APIs")
public class AuthController {

    private final AuthService authService;
    private final com.featureflag.auth_service.service.PasswordResetService passwordResetService;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(
            AuthService authService,
            com.featureflag.auth_service.service.PasswordResetService passwordResetService
    ) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    public AuthController(AuthService authService) {
        this(authService, null);
    }

    @Operation(summary = "Login and get JWT token")
    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request) {

        return authService.login(request);
    }

    @Operation(summary = "Request password reset email")
    @PostMapping("/forgot-password")
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> forgotPassword(
            @Valid @RequestBody com.featureflag.auth_service.dto.ForgotPasswordRequest request) {
        String message = passwordResetService.requestPasswordReset(request);
        return org.springframework.http.ResponseEntity.ok(
                java.util.Map.of("message", message)
        );
    }

    @Operation(summary = "Reset password using recovery token")
    @PostMapping("/reset-password")
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> resetPassword(
            @Valid @RequestBody com.featureflag.auth_service.dto.ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return org.springframework.http.ResponseEntity.ok(
                java.util.Map.of("message", "Password has been reset successfully. Please log in with your new password.")
        );
    }

    @Operation(summary = "Access protected profile")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/profile")
    public ProfileResponse profile(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return new ProfileResponse(user.getName(), user.getEmail(), user.getRole());
    }

    @Operation(summary = "Get active notification recipients by role")
    @GetMapping("/recipients")
    public List<String> getNotificationRecipients(
            @RequestParam(required = false) List<String> roles) {

        return authService.getNotificationRecipients(roles);
    }

    @Operation(summary = "Change own password")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/change-password")
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> changePassword(
            Authentication authentication,
            @Valid @RequestBody com.featureflag.auth_service.dto.ChangePasswordRequest request) {
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("Authentication required");
        }
        Long callerUserId = null;
        Long callerTokenVersion = null;
        if (authentication.getPrincipal() instanceof User authUser) {
            callerUserId = authUser.getId();
            callerTokenVersion = authUser.getCredentialVersion();
        }
        authService.changePassword(authentication.getName(), callerUserId, callerTokenVersion, request);
        return org.springframework.http.ResponseEntity.ok(
                java.util.Map.of("message", "Password changed successfully. Please log in again.")
        );
    }
}
