package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.config.SecurityConfig;
import com.featureflag.auth_service.dto.UserAccountStatusResponse;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.exception.ResourceNotFoundException;
import com.featureflag.auth_service.security.AuthInternalServiceKeyFilter;
import com.featureflag.auth_service.security.AuthRecipientsServiceKeyFilter;
import com.featureflag.auth_service.security.CustomUserDetailsService;
import com.featureflag.auth_service.security.JwtAuthenticationFilter;
import com.featureflag.auth_service.security.JwtService;
import com.featureflag.auth_service.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalUserController.class)
@Import({
        SecurityConfig.class,
        AuthRecipientsServiceKeyFilter.class,
        AuthInternalServiceKeyFilter.class,
        JwtAuthenticationFilter.class
})
@TestPropertySource(properties = {
        "AUTH_RECIPIENTS_SERVICE_KEY=test-recipients-key",
        "AUTH_INTERNAL_SERVICE_KEY=test-internal-key"
})
class InternalUserControllerSecurityTest {

    private static final String SERVICE_KEY = "test-internal-key";
    private static final String STATUS_URL = "/internal/users/status";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void statusEndpointWithoutServiceKeyReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(STATUS_URL)
                        .param("email", "user@company.com")
                        .param("userId", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusEndpointWithWrongServiceKeyReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(STATUS_URL)
                        .header(AuthInternalServiceKeyFilter.HEADER_NAME, "wrong-key")
                        .param("email", "user@company.com")
                        .param("userId", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusEndpointWithBlankServiceKeyReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(STATUS_URL)
                        .header(AuthInternalServiceKeyFilter.HEADER_NAME, "   ")
                        .param("email", "user@company.com")
                        .param("userId", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerTokenWithoutServiceKeyReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(STATUS_URL)
                        .header("Authorization", "Bearer some-token")
                        .param("email", "user@company.com")
                        .param("userId", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusEndpointWithValidKeyAndMatchingIdentityReturnsStatus() throws Exception {
        when(authService.getUserAccountStatus("user@company.com", 10L))
                .thenReturn(new UserAccountStatusResponse(10L, "user@company.com", Role.ADMIN, true));

        mockMvc.perform(get(STATUS_URL)
                        .header(AuthInternalServiceKeyFilter.HEADER_NAME, SERVICE_KEY)
                        .param("email", "user@company.com")
                        .param("userId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.email").value("user@company.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.credentialVersion").value(1));
    }

    @Test
    void statusEndpointMissingUserIdParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get(STATUS_URL)
                        .header(AuthInternalServiceKeyFilter.HEADER_NAME, SERVICE_KEY)
                        .param("email", "user@company.com"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusEndpointWithIdentityMismatchReturnsNotFound() throws Exception {
        when(authService.getUserAccountStatus("user@company.com", 101L))
                .thenThrow(new ResourceNotFoundException("User identity mismatch"));

        mockMvc.perform(get(STATUS_URL)
                        .header(AuthInternalServiceKeyFilter.HEADER_NAME, SERVICE_KEY)
                        .param("email", "user@company.com")
                        .param("userId", "101"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusEndpointForDeletedUserReturnsNotFound() throws Exception {
        when(authService.getUserAccountStatus("deleted@company.com", 50L))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(get(STATUS_URL)
                        .header(AuthInternalServiceKeyFilter.HEADER_NAME, SERVICE_KEY)
                        .param("email", "deleted@company.com")
                        .param("userId", "50"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusEndpointForDisabledUserReturnsEnabledFalse() throws Exception {
        when(authService.getUserAccountStatus("disabled@company.com", 20L))
                .thenReturn(new UserAccountStatusResponse(20L, "disabled@company.com", Role.DEVELOPER, false));

        mockMvc.perform(get(STATUS_URL)
                        .header(AuthInternalServiceKeyFilter.HEADER_NAME, SERVICE_KEY)
                        .param("email", "disabled@company.com")
                        .param("userId", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(20))
                .andExpect(jsonPath("$.enabled").value(false));
    }
}
