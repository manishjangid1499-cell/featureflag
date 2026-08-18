package com.featureflag.notification_service.controller;

import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.security.JwtSecurityConfig;
import com.featureflag.notification_service.security.SecurityConfig;
import com.featureflag.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, NotificationAuthorizationTest.TestJwtConfiguration.class})
class NotificationAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void ownerCanQueryOrganizationWideStatus() throws Exception {
        authenticate("owner-token", "owner@company.com", "OWNER");
        when(notificationService.getNotificationsByStatus("SENT"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/status/SENT")
                        .header("Authorization", "Bearer owner-token"))
                .andExpect(status().isOk());
    }

    @Test
    void adminCannotQueryOrganizationWideStatus() throws Exception {
        authenticate("admin-token", "admin@company.com", "ADMIN");

        mockMvc.perform(get("/api/notifications/status/SENT")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void developerCannotCreateNotification() throws Exception {
        authenticate("developer-token", "developer@company.com", "DEVELOPER");

        mockMvc.perform(post("/api/notifications")
                        .header("Authorization", "Bearer developer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validNotificationJson("forged@company.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotDeleteNotification() throws Exception {
        authenticate("viewer-token", "viewer@company.com", "VIEWER");

        mockMvc.perform(delete("/api/notifications/1")
                        .header("Authorization", "Bearer viewer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void restCreatorIdentityComesFromAuthenticatedPrincipal() throws Exception {
        authenticate("admin-token", "admin@company.com", "ADMIN");
        when(notificationService.createNotification(
                any(NotificationRequest.class),
                eq("admin@company.com")
        )).thenReturn(Notification.builder()
                .id(1L)
                .recipient("recipient@company.com")
                .creatorEmail("admin@company.com")
                .subject("Alert")
                .message("Message")
                .type("EMAIL")
                .status("SENT")
                .build());

        mockMvc.perform(post("/api/notifications")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validNotificationJson("forged@company.com")))
                .andExpect(status().isCreated());

        verify(notificationService).createNotification(
                any(NotificationRequest.class),
                eq("admin@company.com")
        );
    }

    private void authenticate(String token, String email, String role) {
        Instant now = Instant.now();
        when(jwtDecoder.decode(token)).thenReturn(new Jwt(
                token,
                now.minusSeconds(10),
                now.plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("sub", email, "role", role)
        ));
    }

    private String validNotificationJson(String creatorEmail) {
        return """
                {
                  "recipient": "recipient@company.com",
                  "creatorEmail": "%s",
                  "subject": "Alert",
                  "message": "Message",
                  "type": "EMAIL"
                }
                """.formatted(creatorEmail);
    }

    @TestConfiguration
    static class TestJwtConfiguration {

        @Bean
        Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
            return new JwtSecurityConfig().jwtAuthenticationConverter();
        }
    }
}
