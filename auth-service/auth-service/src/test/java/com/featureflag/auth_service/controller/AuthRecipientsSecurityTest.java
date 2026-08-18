package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.config.SecurityConfig;
import com.featureflag.auth_service.dto.TokenValidationResponse;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void validateEndpointRemainsPublicAndUnchanged() throws Exception {
        when(authService.validateToken("existing-token"))
                .thenReturn(new TokenValidationResponse(
                        true,
                        "user@company.com",
                        "VIEWER"
                ));

        mockMvc.perform(get("/auth/validate")
                        .queryParam("token", "existing-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.email").value("user@company.com"))
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }
}
