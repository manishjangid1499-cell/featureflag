package com.featureflag.auth_service.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.auth_service.observability.CorrelationIds;
import com.featureflag.auth_service.security.LoginRateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-05T10:00:00Z"),
                ZoneOffset.UTC
        );
        handler = new GlobalExceptionHandler(
                new ApiProblemDetails(new ObjectMapper(), clock)
        );
        request = new MockHttpServletRequest(
                "POST",
                "/auth/invitations"
        );
    }

    @Test
    @DisplayName("Invitation conflict returns HTTP 409 ProblemDetail")
    void testInvitationConflict_ReturnsConflict() {
        InvitationConflictException exception =
                new InvitationConflictException(
                        "Invitation can no longer be accepted."
                );

        ResponseEntity<ProblemDetail> response =
                handler.handleInvitationConflict(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().getStatus());
        assertEquals("Conflict", response.getBody().getTitle());
        assertEquals(
                "Invitation can no longer be accepted.",
                response.getBody().getDetail()
        );
        assertEquals(
                "2026-09-05T10:00:00Z",
                response.getBody().getProperties().get("timestamp")
        );
    }

    @Test
    @DisplayName("Login rate limit returns generic HTTP 429 with Retry-After")
    void loginRateLimitReturnsTooManyRequests() {
        ResponseEntity<ProblemDetail> response =
                handler.handleLoginRateLimit(
                        new LoginRateLimitExceededException(37),
                        request
                );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("37", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertNotNull(response.getBody());
        assertEquals(
                "Too many login attempts. Try again later.",
                response.getBody().getDetail()
        );
    }

    @Test
    @DisplayName("Unexpected failures are sanitized and include correlation")
    void unexpectedFailureIsSanitizedAndCorrelated() {
        MDC.put(CorrelationIds.MDC_KEY, "corr-test-123");
        try {
            ResponseEntity<ProblemDetail> response =
                    handler.handleGeneralException(
                            new IllegalStateException("password=TOP_SECRET"),
                            request
                    );

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("An unexpected error occurred", response.getBody().getDetail());
            assertEquals(
                    "corr-test-123",
                    response.getBody().getProperties().get("correlationId")
            );
            assertFalse(response.getBody().toString().contains("TOP_SECRET"));
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }
}
