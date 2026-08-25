package com.featureflag.auth_service.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("Invitation conflict returns HTTP 409 using the existing error format")
    void testInvitationConflict_ReturnsConflict() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        InvitationConflictException exception =
                new InvitationConflictException(
                        "Invitation can no longer be accepted."
                );

        ResponseEntity<Map<String, Object>> response =
                handler.handleInvitationConflict(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().get("status"));
        assertEquals("Conflict", response.getBody().get("error"));
        assertEquals(
                "Invitation can no longer be accepted.",
                response.getBody().get("message")
        );
        assertNotNull(response.getBody().get("timestamp"));
    }
}
