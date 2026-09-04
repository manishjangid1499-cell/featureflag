package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.AuthResponse;
import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.observability.AuthMetrics;
import com.featureflag.auth_service.security.JwtService;
import com.featureflag.auth_service.security.LoginRateLimitExceededException;
import com.featureflag.auth_service.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @Mock
    private AuthMetrics authMetrics;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@company.com")
                .password("encoded_password")
                .role(Role.DEVELOPER)
                .build();
    }

    @Test
    @DisplayName("Login - normalizes email and returns JWT and role")
    void testLogin_Success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("  Test@Company.COM  ");
        request.setPassword("password123");

        when(userRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "encoded_password")).thenReturn(true);
        when(jwtService.generateToken("test@company.com", "DEVELOPER")).thenReturn("mocked_jwt_token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked_jwt_token", response.getToken());
        assertEquals("test@company.com", response.getEmail());
        assertEquals("DEVELOPER", response.getRole());
        verify(loginRateLimiter).checkAllowed("test@company.com");
        verify(loginRateLimiter).recordSuccess("test@company.com");
        verify(authMetrics).loginSucceeded();
    }

    @Test
    @DisplayName("Login - unknown user returns generic bad credentials")
    void testLogin_UserNotFound_ThrowsBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("unknown@company.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("unknown@company.com")).thenReturn(Optional.empty());

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals("Invalid email or password", exception.getMessage());
        verify(loginRateLimiter).recordFailure("unknown@company.com");
        verify(authMetrics).loginFailed();
    }

    @Test
    @DisplayName("Login - incorrect password returns same generic bad credentials")
    void testLogin_InvalidPassword_ThrowsBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@company.com");
        request.setPassword("wrongpassword");

        when(userRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", "encoded_password")).thenReturn(false);

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals("Invalid email or password", exception.getMessage());
        verify(loginRateLimiter).recordFailure("test@company.com");
        verify(authMetrics).loginFailed();
    }

    @Test
    @DisplayName("Login - disabled user returns generic bad credentials")
    void testLogin_DisabledUser_ThrowsBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@company.com");
        request.setPassword("password123");
        testUser.setEnabled(false);

        when(userRepository.findByEmail("test@company.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(
                "password123",
                "encoded_password"
        )).thenReturn(true);

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals("Invalid email or password", exception.getMessage());
        verify(loginRateLimiter).recordFailure("test@company.com");
        verify(authMetrics).loginFailed();
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Login - re-enabled user can authenticate")
    void testLogin_ReEnabledUser_Succeeds() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@company.com");
        request.setPassword("password123");
        testUser.setEnabled(false);
        testUser.setEnabled(true);

        when(userRepository.findByEmail("test@company.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(
                "password123",
                "encoded_password"
        )).thenReturn(true);
        when(jwtService.generateToken(
                "test@company.com",
                "DEVELOPER"
        )).thenReturn("new-token");

        assertEquals("new-token", authService.login(request).getToken());
        verify(loginRateLimiter).recordSuccess("test@company.com");
    }

    @Test
    @DisplayName("Login - an active rate limit stops authentication before lookup")
    void testLogin_RateLimited_DoesNotQueryRepository() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@company.com");
        request.setPassword("password123");
        doThrow(new LoginRateLimitExceededException(42))
                .when(loginRateLimiter)
                .checkAllowed("test@company.com");

        assertThrows(
                LoginRateLimitExceededException.class,
                () -> authService.login(request)
        );

        verifyNoInteractions(userRepository, passwordEncoder, jwtService);
    }

    @Test
    @DisplayName("Login - infrastructure failure is not recorded as a credential failure")
    void testLogin_ServerError_NotCountedAsCredentialFailure() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@company.com");
        request.setPassword("password123");
        when(userRepository.findByEmail("test@company.com"))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> authService.login(request)
        );

        verify(loginRateLimiter, never()).recordFailure(anyString());
        verify(authMetrics, never()).loginFailed();
    }

    @Test
    @DisplayName("Get Notification Recipients - normalizes OWNER and ADMIN emails")
    void testGetNotificationRecipients() {
        User owner = User.builder().email(" OWNER@Company.COM ").role(Role.OWNER).build();
        User admin = User.builder().email("admin@company.com").role(Role.ADMIN).build();
        User disabledAdmin = User.builder()
                .email("disabled@company.com")
                .role(Role.ADMIN)
                .enabled(false)
                .build();

        when(userRepository.findByRoleIn(anyCollection()))
                .thenReturn(List.of(owner, admin, disabledAdmin));

        List<String> recipients = authService.getNotificationRecipients(List.of("OWNER", "ADMIN"));

        assertNotNull(recipients);
        assertEquals(2, recipients.size());
        assertTrue(recipients.contains("owner@company.com"));
        assertTrue(recipients.contains("admin@company.com"));
    }
}
