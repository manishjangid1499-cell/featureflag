package com.featureflag.notification_service.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new ApiProblemDetails(
                    new ObjectMapper(),
                    Clock.fixed(Instant.parse("2026-09-05T10:00:00Z"), ZoneOffset.UTC)
            ));
    private final MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/internal/notifications/invitations");

    @Test
    void invitationDeliveryFailureMapsToSafeHttp502() {
        ResponseEntity<ProblemDetail> response =
                handler.handleInvitationDelivery(
                        new InvitationDeliveryException(),
                        request
                );

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(502, response.getBody().getStatus());
        assertEquals("Bad Gateway", response.getBody().getTitle());
        assertEquals(
                "Invitation email delivery failed",
                response.getBody().getDetail()
        );
    }

    @Test
    void notificationConflictMapsToHttp409() {
        ResponseEntity<ProblemDetail> response =
                handler.handleNotificationConflict(
                        new NotificationConflictException(
                                "Active notification delivery cannot be deleted"
                        ),
                        request
                );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("Conflict", response.getBody().getTitle());
        assertEquals(
                "Active notification delivery cannot be deleted",
                response.getBody().getDetail()
        );
    }
}
