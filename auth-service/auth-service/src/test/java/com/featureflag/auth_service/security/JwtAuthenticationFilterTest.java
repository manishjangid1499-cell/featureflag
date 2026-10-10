package com.featureflag.auth_service.security;

import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rsaTokenReloadsUserAndUsesCurrentDatabaseRoleAndUserPrincipal() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        User currentUser = User.builder()
                .id(10L)
                .email("user@company.com")
                .password("encoded")
                .role(Role.OWNER)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer rsa-token");
        when(jwtService.extractEmail("rsa-token")).thenReturn("user@company.com");
        when(jwtService.extractUserId("rsa-token")).thenReturn(10L);
        when(jwtService.extractTokenVersion("rsa-token")).thenReturn(1L);
        when(userDetailsService.loadUserByUsername("user@company.com")).thenReturn(currentUser);
        when(jwtService.isTokenValid("rsa-token", "user@company.com")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertSame(currentUser, authentication.getPrincipal());
        assertEquals("user@company.com", authentication.getName());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER")));
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingTokenVersionClaimDoesNotAuthenticate() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        User currentUser = User.builder()
                .id(10L)
                .email("user@company.com")
                .password("encoded")
                .role(Role.OWNER)
                .credentialVersion(1L)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer versionless-token");
        when(jwtService.extractEmail("versionless-token")).thenReturn("user@company.com");
        when(jwtService.extractUserId("versionless-token")).thenReturn(10L);
        when(jwtService.extractTokenVersion("versionless-token")).thenReturn(null);
        when(userDetailsService.loadUserByUsername("user@company.com")).thenReturn(currentUser);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void mismatchedTokenVersionDoesNotAuthenticate() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        User currentUser = User.builder()
                .id(10L)
                .email("user@company.com")
                .password("encoded")
                .role(Role.OWNER)
                .credentialVersion(2L) // Password was changed to version 2
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer stale-token");
        when(jwtService.extractEmail("stale-token")).thenReturn("user@company.com");
        when(jwtService.extractUserId("stale-token")).thenReturn(10L);
        when(jwtService.extractTokenVersion("stale-token")).thenReturn(1L); // Old token has version 1
        when(userDetailsService.loadUserByUsername("user@company.com")).thenReturn(currentUser);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void disabledUserIsNotAuthenticatedByAnExistingToken() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService =
                mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtService, userDetailsService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        User disabledUser = User.builder()
                .id(20L)
                .email("disabled@company.com")
                .password("encoded")
                .role(Role.ADMIN)
                .enabled(false)
                .build();

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer existing-token");
        when(jwtService.extractEmail("existing-token"))
                .thenReturn("disabled@company.com");
        when(jwtService.extractUserId("existing-token"))
                .thenReturn(20L);
        when(userDetailsService.loadUserByUsername(
                "disabled@company.com"
        )).thenReturn(disabledUser);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingUserIdClaimDoesNotAuthenticate() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer legacy-token");
        when(jwtService.extractEmail("legacy-token")).thenReturn("user@company.com");
        when(jwtService.extractUserId("legacy-token"))
                .thenThrow(new IllegalArgumentException("JWT is missing valid numeric userId claim"));

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void tokenWithDeletedUserIdDoesNotAuthenticateAsRecreatedUser() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        User recreatedUser = User.builder()
                .id(202L)
                .email("user@company.com")
                .password("encoded")
                .role(Role.ADMIN)
                .enabled(true)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer old-token");
        when(jwtService.extractEmail("old-token")).thenReturn("user@company.com");
        when(jwtService.extractUserId("old-token")).thenReturn(101L); // Old account ID
        when(userDetailsService.loadUserByUsername("user@company.com")).thenReturn(recreatedUser);

        filter.doFilterInternal(request, response, chain);

        // Mismatched userId between token (101) and DB user (202) must NOT authenticate
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }
}
