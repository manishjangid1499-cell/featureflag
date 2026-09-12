package com.featureflag.auth_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.auth_service.exception.ApiProblemDetails;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuthRecipientsServiceKeyFilterTest {

    @Test
    void blankConfiguredKeyFailsClosed() throws Exception {
        AuthRecipientsServiceKeyFilter filter = filter("   ");
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        request.addHeader(
                AuthRecipientsServiceKeyFilter.HEADER_NAME,
                "provided-key"
        );

        filter.doFilterInternal(request, response, chain);

        org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(
                response.getContentType().startsWith("application/problem+json")
        );
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void blankProvidedKeyIsDenied() throws Exception {
        AuthRecipientsServiceKeyFilter filter = filter("configured-key");
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        request.addHeader(AuthRecipientsServiceKeyFilter.HEADER_NAME, " ");

        filter.doFilterInternal(request, response, chain);

        org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    private AuthRecipientsServiceKeyFilter filter(String key) {
        return new AuthRecipientsServiceKeyFilter(
                key,
                new ApiProblemDetails(new ObjectMapper(), Clock.systemUTC())
        );
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/auth/recipients"
        );
        request.setServletPath("/auth/recipients");
        return request;
    }
}
