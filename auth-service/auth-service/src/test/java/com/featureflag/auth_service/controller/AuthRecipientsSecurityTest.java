package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.config.SecurityConfig;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.security.AuthRecipientsServiceKeyFilter;
import com.featureflag.auth_service.security.CustomUserDetailsService;
import com.featureflag.auth_service.security.JwtAuthenticationFilter;
import com.featureflag.auth_service.security.JwtService;
import com.featureflag.auth_service.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        AuthRecipientsServiceKeyFilter.class,
        JwtAuthenticationFilter.class
})
@TestPropertySource(properties = "AUTH_RECIPIENTS_SERVICE_KEY=test-recipients-key")
class AuthRecipientsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void recipientsWithoutServiceKeyReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/recipients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recipientsWithWrongServiceKeyReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/recipients")
                        .header(AuthRecipientsServiceKeyFilter.HEADER_NAME, "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recipientsWithBlankProvidedKeyReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/recipients")
                        .header(AuthRecipientsServiceKeyFilter.HEADER_NAME, "   "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerBearerWithoutServiceKeyStillReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/recipients")
                        .header("Authorization", "Bearer owner-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recipientsWithCorrectServiceKeyReturnsRecipients() throws Exception {
        when(authService.getNotificationRecipients(anyList()))
                .thenReturn(List.of("owner@company.com", "admin@company.com"));

        mockMvc.perform(get("/auth/recipients")
                        .queryParam("roles", "OWNER", "ADMIN")
                        .header(AuthRecipientsServiceKeyFilter.HEADER_NAME, "test-recipients-key"))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"owner@company.com\",\"admin@company.com\"]"));
    }

    @Test
    void protectedProfileAcceptsValidRsaToken() throws Exception {
        authenticateToken("valid-rsa-token", Role.VIEWER, true);

        mockMvc.perform(get("/auth/profile")
                        .header("Authorization", "Bearer valid-rsa-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("Welcome to Protected Profile"));
    }

    @Test
    void protectedProfileRejectsInvalidToken() throws Exception {
        when(jwtService.extractEmail("invalid-token"))
                .thenThrow(new IllegalArgumentException("Invalid JWT"));

        mockMvc.perform(get("/auth/profile")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedProfileRejectsExpiredToken() throws Exception {
        authenticateToken("expired-token", Role.VIEWER, false);

        mockMvc.perform(get("/auth/profile")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerRoleStillCannotAccessMemberEndpoints() throws Exception {
        authenticateToken("viewer-token", Role.VIEWER, true);

        mockMvc.perform(get("/members")
                        .header("Authorization", "Bearer viewer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validateEndpointIsNoLongerMapped() throws Exception {
        authenticateToken("valid-rsa-token", Role.VIEWER, true);

        mockMvc.perform(get("/auth/validate")
                        .header("Authorization", "Bearer valid-rsa-token"))
                .andExpect(status().isNotFound());
    }

    private void authenticateToken(String token, Role role, boolean valid) {
        User currentUser = User.builder()
                .email("user@company.com")
                .password("encoded")
                .role(role)
                .build();
        when(jwtService.extractEmail(token)).thenReturn("user@company.com");
        when(customUserDetailsService.loadUserByUsername("user@company.com"))
                .thenReturn(currentUser);
        when(jwtService.isTokenValid(token, "user@company.com"))
                .thenReturn(valid);
    }
}
