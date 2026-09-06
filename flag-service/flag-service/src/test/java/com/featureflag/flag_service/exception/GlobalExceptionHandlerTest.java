package com.featureflag.flag_service.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {

        handler =
                new GlobalExceptionHandler(new ApiProblemDetails(
                        new ObjectMapper(),
                        Clock.fixed(
                                Instant.parse("2026-09-01T10:00:00Z"),
                                ZoneOffset.UTC
                        )
                ));

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

        ResponseEntity<ProblemDetail> response =
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
                response.getBody().getTitle()
        );

        assertEquals(
                "Feature flag conflicts with existing key and environment",
                response.getBody().getDetail()
        );

        assertEquals(
                "/flags",
                response.getBody().getInstance().toString()
        );
        assertEquals(
                MediaType.APPLICATION_PROBLEM_JSON,
                response.getHeaders().getContentType()
        );
    }

    @Test
    void optimisticLockFailureReturnsConflict() {
        ResponseEntity<ProblemDetail> response =
                handler.handleOptimisticLock(
                        new ObjectOptimisticLockingFailureException(
                                "FeatureFlag",
                                1L
                        ),
                        request
                );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "Feature flag was modified by another request",
                response.getBody().getDetail()
        );
        assertEquals(
                "2026-09-01T10:00:00Z",
                response.getBody().getProperties().get("timestamp")
        );
    }
}
