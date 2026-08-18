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
                .email("user@company.com")
                .password("encoded")
                .role(Role.OWNER)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer rsa-token");
        when(jwtService.extractEmail("rsa-token")).thenReturn("user@company.com");
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
}
