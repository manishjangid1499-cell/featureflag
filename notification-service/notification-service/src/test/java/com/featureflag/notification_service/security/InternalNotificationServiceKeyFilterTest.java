package com.featureflag.notification_service.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class InternalNotificationServiceKeyFilterTest {

    private static final String EXPECTED_KEY =
            "test-notification-internal-key";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingServiceKeyIsRejected() throws Exception {
        InternalNotificationServiceKeyFilter filter =
                new InternalNotificationServiceKeyFilter(
                        EXPECTED_KEY
                );

        MockHttpServletRequest request = internalRequest();
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        AtomicBoolean chainInvoked =
                new AtomicBoolean(false);

        filter.doFilter(
                request,
                response,
                (req, res) -> chainInvoked.set(true)
        );

        assertEquals(401, response.getStatus());
        assertFalse(chainInvoked.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void wrongServiceKeyIsRejected() throws Exception {
        InternalNotificationServiceKeyFilter filter =
                new InternalNotificationServiceKeyFilter(
                        EXPECTED_KEY
                );

        MockHttpServletRequest request = internalRequest();
        request.addHeader(
                InternalNotificationServiceKeyFilter.HEADER_NAME,
                "wrong-key"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();
        AtomicBoolean chainInvoked =
                new AtomicBoolean(false);

        filter.doFilter(
                request,
                response,
                (req, res) -> chainInvoked.set(true)
        );

        assertEquals(401, response.getStatus());
        assertFalse(chainInvoked.get());
    }

    @Test
    void correctServiceKeyCreatesInternalAuthentication()
            throws Exception {

        InternalNotificationServiceKeyFilter filter =
                new InternalNotificationServiceKeyFilter(
                        EXPECTED_KEY
                );

        MockHttpServletRequest request = internalRequest();
        request.addHeader(
                InternalNotificationServiceKeyFilter.HEADER_NAME,
                EXPECTED_KEY
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();
        AtomicBoolean chainInvoked =
                new AtomicBoolean(false);

        filter.doFilter(
                request,
                response,
                (req, res) -> chainInvoked.set(true)
        );

        assertTrue(chainInvoked.get());
        assertEquals(200, response.getStatus());

        var authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        assertNotNull(authentication);
        assertEquals("auth-service", authentication.getName());
        assertTrue(
                authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                                InternalNotificationServiceKeyFilter.AUTHORITY
                                        .equals(
                                                authority.getAuthority()
                                        )
                        )
        );
    }

    @Test
    void unrelatedPathsAreNotIntercepted() throws Exception {
        InternalNotificationServiceKeyFilter filter =
                new InternalNotificationServiceKeyFilter(
                        EXPECTED_KEY
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "GET",
                        "/api/notifications"
                );
        request.setServletPath("/api/notifications");

        MockHttpServletResponse response =
                new MockHttpServletResponse();
        AtomicBoolean chainInvoked =
                new AtomicBoolean(false);

        filter.doFilter(
                request,
                response,
                (req, res) -> chainInvoked.set(true)
        );

        assertTrue(chainInvoked.get());
    }

    private MockHttpServletRequest internalRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        InternalNotificationServiceKeyFilter.INVITATION_PATH
                );
        request.setServletPath(
                InternalNotificationServiceKeyFilter.INVITATION_PATH
        );
        return request;
    }
}
