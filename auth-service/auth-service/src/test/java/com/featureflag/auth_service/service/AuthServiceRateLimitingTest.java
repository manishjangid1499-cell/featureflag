package com.featureflag.auth_service.service;

import com.featureflag.auth_service.config.LoginRateLimitProperties;
import com.featureflag.auth_service.dto.AuthResponse;
import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.observability.AuthMetrics;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.security.InMemoryLoginAttemptStore;
import com.featureflag.auth_service.security.JwtService;
import com.featureflag.auth_service.security.LoginRateLimitExceededException;
import com.featureflag.auth_service.security.LoginRateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceRateLimitingTest {

    private UserRepository repository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties();
        properties.setMaxFailures(2);
        properties.setWindow(Duration.ofMinutes(5));
        properties.setMaxEntries(100);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthMetrics metrics = new AuthMetrics(registry);
        LoginRateLimiter limiter = new LoginRateLimiter(
                new InMemoryLoginAttemptStore(
                        properties,
                        Clock.fixed(
                                Instant.parse("2026-01-01T00:00:00Z"),
                                ZoneOffset.UTC
                        )
                ),
                properties,
                metrics
        );
        repository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        service = new AuthService(
                repository,
                passwordEncoder,
                jwtService,
                limiter,
                metrics
        );
    }

    @Test
    void repeatedCredentialFailuresEventuallyBlockWith429Exception()
            throws Exception {
        when(repository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(request(
                "missing@example.com", "wrong"
        ))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.login(request(
                "missing@example.com", "wrong"
        ))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.login(request(
                "missing@example.com", "wrong"
        )))
                .isInstanceOf(LoginRateLimitExceededException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(300L);
    }

    @Test
    void correctPasswordAfterOrdinaryFailureSucceedsAndResetsState() {
        User user = User.builder()
                .email("user@example.com")
                .password("encoded")
                .role(Role.DEVELOPER)
                .build();
        when(repository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);
        when(passwordEncoder.matches("correct", "encoded")).thenReturn(true);
        when(jwtService.generateToken("user@example.com", "DEVELOPER"))
                .thenReturn("jwt");

        assertThatThrownBy(() -> service.login(request(
                "user@example.com", "wrong"
        ))).isInstanceOf(BadCredentialsException.class);

        AuthResponse response = service.login(request(
                "user@example.com", "correct"
        ));
        assertThat(response.getToken()).isEqualTo("jwt");

        assertThatThrownBy(() -> service.login(request(
                "user@example.com", "wrong"
        ))).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void failuresForDifferentAccountsRemainIsolated() {
        when(repository.findByEmail("one@example.com"))
                .thenReturn(Optional.empty());
        when(repository.findByEmail("two@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(request(
                "one@example.com", "wrong"
        ))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.login(request(
                "one@example.com", "wrong"
        ))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.login(request(
                "two@example.com", "wrong"
        ))).isInstanceOf(BadCredentialsException.class);
    }

    private LoginRequest request(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
