package com.featureflag.notification_service.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void invitationDeliveryFailureMapsToSafeHttp502() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleInvitationDelivery(
                        new InvitationDeliveryException()
                );

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(502, response.getBody().get("status"));
        assertEquals("Bad Gateway", response.getBody().get("error"));
        assertEquals(
                "Invitation email delivery failed",
                response.getBody().get("message")
        );
    }

    @Test
    void notificationConflictMapsToHttp409() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleNotificationConflict(
                        new NotificationConflictException(
                                "Active notification delivery cannot be deleted"
                        )
                );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("Conflict", response.getBody().get("error"));
        assertEquals(
                "Active notification delivery cannot be deleted",
                response.getBody().get("message")
        );
    }
}
