package com.featureflag.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.auth_service.config.SecurityConfig;
import com.featureflag.auth_service.dto.ChangePasswordRequest;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.exception.GlobalExceptionHandler;
import com.featureflag.auth_service.exception.InvalidOperationException;
import com.featureflag.auth_service.security.AuthInternalServiceKeyFilter;
import com.featureflag.auth_service.security.AuthRecipientsServiceKeyFilter;
import com.featureflag.auth_service.security.CustomUserDetailsService;
import com.featureflag.auth_service.security.JwtAuthenticationFilter;
import com.featureflag.auth_service.security.JwtService;
import com.featureflag.auth_service.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        AuthRecipientsServiceKeyFilter.class,
        AuthInternalServiceKeyFilter.class,
        JwtAuthenticationFilter.class
})
@TestPropertySource(properties = {
        "AUTH_RECIPIENTS_SERVICE_KEY=test-recipients-key",
        "AUTH_INTERNAL_SERVICE_KEY=test-internal-key"
})
class ChangePasswordControllerSecurityTest {

    private static final String CHANGE_PASSWORD_URL = "/auth/change-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private com.featureflag.auth_service.service.PasswordResetService passwordResetService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private User authenticatedUser;

    @BeforeEach
    void setUp() {
        authenticatedUser = User.builder()
                .id(42L)
                .name("Alice")
                .email("alice@company.com")
                .role(Role.DEVELOPER)
                .credentialVersion(1L)
                .enabled(true)
                .build();
    }

    private void authenticate() {
        when(jwtService.extractEmail("valid-token")).thenReturn("alice@company.com");
        when(jwtService.extractUserId("valid-token")).thenReturn(42L);
        when(jwtService.extractTokenVersion("valid-token")).thenReturn(1L);
        when(customUserDetailsService.loadUserByUsername("alice@company.com")).thenReturn(authenticatedUser);
        when(jwtService.isTokenValid("valid-token", "alice@company.com")).thenReturn(true);
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "currentPassword123",
                "newPassword456",
                "newPassword456"
        );

        mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void authenticatedRequestIdentifiesUserFromSecurityContextAndSucceeds() throws Exception {
        authenticate();
        ChangePasswordRequest request = new ChangePasswordRequest(
                "currentPassword123",
                "newPassword456",
                "newPassword456"
        );

        mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Verifies user email is taken strictly from Authentication principal, not request body
        verify(authService).changePassword(eq("alice@company.com"), eq(42L), eq(1L), eq(request));
    }

    @Test
    void incorrectCurrentPasswordReturnsBadRequestWithoutSessionTearDown() throws Exception {
        authenticate();
        ChangePasswordRequest request = new ChangePasswordRequest(
                "wrongPassword",
                "newPassword456",
                "newPassword456"
        );

        doThrow(new InvalidOperationException("Current password is incorrect"))
                .when(authService).changePassword(eq("alice@company.com"), eq(42L), eq(1L), any());

        mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Current password is incorrect"));
    }

    @Test
    void passwordShorterThan8CharsReturnsBadRequest() throws Exception {
        authenticate();
        ChangePasswordRequest request = new ChangePasswordRequest(
                "currentPassword123",
                "short",
                "short"
        );

        mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void passwordExceeding72Utf8BytesReturnsBadRequest() throws Exception {
        authenticate();
        String over72Bytes = "a".repeat(73);
        ChangePasswordRequest request = new ChangePasswordRequest(
                "currentPassword123",
                over72Bytes,
                over72Bytes
        );

        mockMvc.perform(post(CHANGE_PASSWORD_URL)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }
}
