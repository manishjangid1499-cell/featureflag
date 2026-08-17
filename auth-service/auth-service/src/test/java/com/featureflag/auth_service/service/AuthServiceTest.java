package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.AuthResponse;
import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.dto.RegisterRequest;
import com.featureflag.auth_service.dto.TokenValidationResponse;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

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
    @DisplayName("Register - Successful Registration as VIEWER")
    void testRegister_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setName("New User");
        request.setEmail("newuser@company.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("newuser@company.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded_password");

        String result = authService.register(request);

        assertNotNull(result);
        assertTrue(result.contains("Successfully"));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Register - Duplicate Email Throws RuntimeException")
    void testRegister_DuplicateEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@company.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertTrue(exception.getMessage().contains("already exists"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Login - Successful Authentication Returns JWT and Role")
    void testLogin_Success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@company.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "encoded_password")).thenReturn(true);
        when(jwtService.generateToken("test@company.com", "DEVELOPER")).thenReturn("mocked_jwt_token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked_jwt_token", response.getToken());
        assertEquals("test@company.com", response.getEmail());
        assertEquals("DEVELOPER", response.getRole());
    }

    @Test
    @DisplayName("Login - User Not Found Throws RuntimeException")
    void testLogin_UserNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("unknown@company.com");
        request.setPassword("password123");

        when(userRepository.findByEmail("unknown@company.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Login - Incorrect Password Throws RuntimeException")
    void testLogin_InvalidPassword_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@company.com");
        request.setPassword("wrongpassword");

        when(userRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", "encoded_password")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Validate Token - Valid Token Returns True with User Details")
    void testValidateToken_ValidToken() {
        String token = "valid_jwt_token";
        when(jwtService.extractEmail(token)).thenReturn("test@company.com");
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(userRepository.findByEmail("test@company.com")).thenReturn(Optional.of(testUser));

        TokenValidationResponse response = authService.validateToken(token);

        assertNotNull(response);
        assertTrue(response.isValid());
        assertEquals("test@company.com", response.getEmail());
        assertEquals("DEVELOPER", response.getRole());
    }

    @Test
    @DisplayName("Validate Token - Invalid Token Returns False")
    void testValidateToken_InvalidToken() {
        String token = "invalid_token";
        when(jwtService.extractEmail(token)).thenThrow(new RuntimeException("Expired JWT"));

        TokenValidationResponse response = authService.validateToken(token);

        assertNotNull(response);
        assertFalse(response.isValid());
        assertNull(response.getEmail());
    }

    @Test
    @DisplayName("Get Notification Recipients - Resolves Active OWNER and ADMIN Emails")
    void testGetNotificationRecipients() {
        User owner = User.builder().email("owner@company.com").role(Role.OWNER).build();
        User admin = User.builder().email("admin@company.com").role(Role.ADMIN).build();

        when(userRepository.findByRoleIn(anyCollection())).thenReturn(List.of(owner, admin));

        List<String> recipients = authService.getNotificationRecipients(List.of("OWNER", "ADMIN"));

        assertNotNull(recipients);
        assertEquals(2, recipients.size());
        assertTrue(recipients.contains("owner@company.com"));
        assertTrue(recipients.contains("admin@company.com"));
    }
}
