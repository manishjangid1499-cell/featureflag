package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.observability.AuthMetrics;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.security.JwtService;
import com.featureflag.auth_service.security.LoginRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
class UserEnabledStatusMySqlIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11");

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistedStatusControlsNewLoginAndSupportsReEnable() {
        BCryptPasswordEncoder passwordEncoder =
                new BCryptPasswordEncoder(4);
        User user = userRepository.saveAndFlush(User.builder()
                .name("Managed User")
                .email("managed@example.test")
                .password(passwordEncoder.encode("valid-password"))
                .role(Role.DEVELOPER)
                .build());

        JwtService jwtService = mock(JwtService.class);
        when(jwtService.generateToken(
                "managed@example.test",
                "DEVELOPER"
        )).thenReturn("issued-token");
        AuthService authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                mock(LoginRateLimiter.class),
                mock(AuthMetrics.class)
        );
        LoginRequest request = new LoginRequest();
        request.setEmail("managed@example.test");
        request.setPassword("valid-password");

        assertThat(user.isEnabled()).isTrue();
        assertThat(authService.login(request).getToken())
                .isEqualTo("issued-token");

        user.setEnabled(false);
        userRepository.saveAndFlush(user);
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");

        user.setEnabled(true);
        userRepository.saveAndFlush(user);
        assertThat(authService.login(request).getToken())
                .isEqualTo("issued-token");
    }
}
