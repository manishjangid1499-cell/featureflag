package com.featureflag.notification_service.controller;

import com.featureflag.notification_service.dto.InvitationEmailRequest;
import com.featureflag.notification_service.exception.InvitationDeliveryException;
import com.featureflag.notification_service.security.InternalNotificationServiceKeyFilter;
import com.featureflag.notification_service.security.JwtSecurityConfig;
import com.featureflag.notification_service.security.SecurityConfig;
import com.featureflag.notification_service.service.InvitationEmailService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalNotificationController.class)
@Import({
        SecurityConfig.class,
        InternalNotificationControllerTest.TestJwtConfiguration.class
})
@TestPropertySource(properties =
        "NOTIFICATION_INTERNAL_SERVICE_KEY=test-notification-internal-key"
)
class InternalNotificationControllerTest {

    private static final String INTERNAL_KEY =
            "test-notification-internal-key";

    private static final String RAW_TOKEN =
            "VERY_SECRET_ENDPOINT_TOKEN";

    private static final String ACCEPTANCE_URL =
            "https://frontend.example.test/accept-invitation?token="
                    + RAW_TOKEN;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvitationEmailService invitationEmailService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void successfulSynchronousDeliveryReturnsNoContent()
            throws Exception {

        mockMvc.perform(post(
                        InternalNotificationServiceKeyFilter.INVITATION_PATH
                )
                        .header(
                                InternalNotificationServiceKeyFilter.HEADER_NAME,
                                INTERNAL_KEY
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(invitationEmailService, times(1))
                .sendInvitationEmail(any(InvitationEmailRequest.class));
    }

    @Test
    void failedSynchronousDeliveryReturnsSafeBadGateway()
            throws Exception {

        when(invitationEmailService.sendInvitationEmail(
                any(InvitationEmailRequest.class)
        )).thenThrow(new InvitationDeliveryException());

        String responseBody = mockMvc.perform(post(
                        InternalNotificationServiceKeyFilter.INVITATION_PATH
                )
                        .header(
                                InternalNotificationServiceKeyFilter.HEADER_NAME,
                                INTERNAL_KEY
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"))
                .andExpect(jsonPath("$.message").value(
                        "Invitation email delivery failed"
                ))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(responseBody.contains(RAW_TOKEN));
        assertFalse(responseBody.contains(ACCEPTANCE_URL));
        assertFalse(responseBody.contains("invitee@company.com"));
        assertFalse(responseBody.contains("SMTP provider response"));
        verify(invitationEmailService, times(1))
                .sendInvitationEmail(any(InvitationEmailRequest.class));
    }

    private String validRequestJson() {
        return """
                {
                  "recipient": "invitee@company.com",
                  "inviteeName": "Invitee",
                  "inviterName": "Owner",
                  "inviterEmail": "owner@company.com",
                  "role": "DEVELOPER",
                  "expirationHours": 48,
                  "acceptanceUrl": "%s"
                }
                """.formatted(ACCEPTANCE_URL);
    }

    @TestConfiguration
    static class TestJwtConfiguration {

        @Bean
        Converter<Jwt, ? extends AbstractAuthenticationToken>
        jwtAuthenticationConverter() {
            return new JwtSecurityConfig()
                    .jwtAuthenticationConverter();
        }
    }
}
