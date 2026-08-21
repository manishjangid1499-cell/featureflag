package com.featureflag.flag_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {

        handler =
                new GlobalExceptionHandler();

        request =
                mock(HttpServletRequest.class);

        when(
                request.getRequestURI()
        ).thenReturn(
                "/flags"
        );
    }

    @Test
    @DisplayName(
            "Duplicate flag key and environment returns HTTP 409 Conflict"
    )
    void testDataIntegrityViolation_ReturnsConflict() {

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Duplicate flag_key and environment"
                );

        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrityViolation(
                        exception,
                        request
                );

        assertEquals(
                HttpStatus.CONFLICT,
                response.getStatusCode()
        );

        assertNotNull(
                response.getBody()
        );

        assertEquals(
                HttpStatus.CONFLICT.value(),
                response.getBody().getStatus()
        );

        assertEquals(
                "Conflict",
                response.getBody().getError()
        );

        assertEquals(
                "Feature flag conflicts with existing key and environment",
                response.getBody().getMessage()
        );

        assertEquals(
                "/flags",
                response.getBody().getPath()
        );
    }
}
