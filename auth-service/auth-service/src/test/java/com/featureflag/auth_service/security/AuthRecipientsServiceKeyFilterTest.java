package com.featureflag.auth_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRecipientsServiceKeyFilterTest {

    @Test
    void blankConfiguredKeyFailsClosed() throws Exception {
        AuthRecipientsServiceKeyFilter filter = new AuthRecipientsServiceKeyFilter("   ");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(AuthRecipientsServiceKeyFilter.HEADER_NAME))
                .thenReturn("provided-key");

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void blankProvidedKeyIsDenied() throws Exception {
        AuthRecipientsServiceKeyFilter filter = new AuthRecipientsServiceKeyFilter("configured-key");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(AuthRecipientsServiceKeyFilter.HEADER_NAME))
                .thenReturn(" ");

        filter.doFilterInternal(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(request, response);
    }
}
