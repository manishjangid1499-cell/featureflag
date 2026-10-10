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

    @Mock
    private com.featureflag.auth_service.repository.PasswordResetTokenRepository passwordResetTokenRepository;

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
        when(jwtService.generateToken(1L, "test@company.com", "DEVELOPER", 1L)).thenReturn("mocked_jwt_token");

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
                1L,
                "test@company.com",
                "DEVELOPER",
                1L
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

    @Test
    @DisplayName("Get Account Status - matching email and matching userId succeeds")
    void testGetUserAccountStatus_MatchingUserId_Success() {
        when(userRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));

        var response = authService.getUserAccountStatus("test@company.com", 1L);

        assertNotNull(response);
        assertEquals(1L, response.userId());
        assertEquals("test@company.com", response.email());
        assertEquals(Role.DEVELOPER, response.role());
        assertTrue(response.enabled());
        assertEquals(1L, response.credentialVersion());
    }

    @Test
    @DisplayName("Get Account Status - null or non-positive userId throws IllegalArgumentException")
    void testGetUserAccountStatus_NullOrInvalidUserId_ThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> authService.getUserAccountStatus("test@company.com", null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> authService.getUserAccountStatus("test@company.com", 0L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> authService.getUserAccountStatus("test@company.com", -1L)
        );
    }

    @Test
    @DisplayName("Get Account Status - userId mismatch throws ResourceNotFoundException")
    void testGetUserAccountStatus_MismatchedUserId_ThrowsNotFound() {
        when(userRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));

        assertThrows(
                com.featureflag.auth_service.exception.ResourceNotFoundException.class,
                () -> authService.getUserAccountStatus("test@company.com", 999L)
        );
    }

    @Test
    @DisplayName("Change Password - successful change updates password hash and increments credential version")
    void testChangePassword_Success() {
        var request = new com.featureflag.auth_service.dto.ChangePasswordRequest(
                "oldPassword123",
                "newPassword456",
                "newPassword456"
        );

        when(userRepository.findByEmailForUpdate("test@company.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPassword123", "encoded_password")).thenReturn(true);
        when(passwordEncoder.matches("newPassword456", "encoded_password")).thenReturn(false);
        when(passwordEncoder.encode("newPassword456")).thenReturn("new_encoded_hash");

        authService.changePassword("test@company.com", request);

        assertEquals("new_encoded_hash", testUser.getPassword());
        assertEquals(2L, testUser.getCredentialVersion());
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Change Password - incorrect current password throws InvalidOperationException without mutating")
    void testChangePassword_IncorrectCurrentPassword() {
        var request = new com.featureflag.auth_service.dto.ChangePasswordRequest(
                "wrongPassword",
                "newPassword456",
                "newPassword456"
        );

        when(userRepository.findByEmailForUpdate("test@company.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "encoded_password")).thenReturn(false);

        var ex = assertThrows(
                com.featureflag.auth_service.exception.InvalidOperationException.class,
                () -> authService.changePassword("test@company.com", request)
        );

        assertTrue(ex.getMessage().contains("Current password is incorrect"));
        assertEquals("encoded_password", testUser.getPassword());
        assertEquals(1L, testUser.getCredentialVersion());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Change Password - password reuse throws InvalidOperationException without mutating")
    void testChangePassword_PasswordReuse() {
        var request = new com.featureflag.auth_service.dto.ChangePasswordRequest(
                "oldPassword123",
                "oldPassword123",
                "oldPassword123"
        );

        when(userRepository.findByEmailForUpdate("test@company.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPassword123", "encoded_password")).thenReturn(true);

        var ex = assertThrows(
                com.featureflag.auth_service.exception.InvalidOperationException.class,
                () -> authService.changePassword("test@company.com", request)
        );

        assertTrue(ex.getMessage().contains("cannot be the same"));
        assertEquals("encoded_password", testUser.getPassword());
        assertEquals(1L, testUser.getCredentialVersion());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Change Password - mismatched confirm password throws InvalidOperationException")
    void testChangePassword_MismatchedConfirmPassword() {
        var request = new com.featureflag.auth_service.dto.ChangePasswordRequest(
                "oldPassword123",
                "newPassword456",
                "differentConfirm"
        );

        var ex = assertThrows(
                com.featureflag.auth_service.exception.InvalidOperationException.class,
                () -> authService.changePassword("test@company.com", request)
        );

        assertTrue(ex.getMessage().contains("do not match"));
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Change Password - short password throws InvalidOperationException")
    void testChangePassword_ShortPassword() {
        var request = new com.featureflag.auth_service.dto.ChangePasswordRequest(
                "oldPassword123",
                "short",
                "short"
        );

        var ex = assertThrows(
                com.featureflag.auth_service.exception.InvalidOperationException.class,
                () -> authService.changePassword("test@company.com", request)
        );

        assertTrue(ex.getMessage().contains("at least 8 characters"));
        verifyNoInteractions(userRepository);
    }
}
