package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.ChangePasswordRequest;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.exception.InvalidOperationException;
import com.featureflag.auth_service.observability.AuthMetrics;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.security.JwtService;
import com.featureflag.auth_service.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ChangePasswordMySqlIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.featureflag.auth_service.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // fast strength for IT
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                mock(JwtService.class),
                mock(LoginRateLimiter.class),
                mock(AuthMetrics.class),
                passwordResetTokenRepository
        );
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("All roles (OWNER, ADMIN, DEVELOPER, VIEWER) can change their own password in MySQL")
    void allRolesCanChangeOwnPassword(Role role) {
        String email = "role-" + role.name().toLowerCase() + "@example.test";
        User user = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("User " + role.name())
                        .email(email)
                        .password(passwordEncoder.encode("initialPassword123"))
                        .role(role)
                        .enabled(true)
                        .build())
        );
        assertThat(user.getCredentialVersion()).isEqualTo(1L);

        // Change password
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ChangePasswordRequest req = new ChangePasswordRequest(
                    "initialPassword123",
                    "updatedPassword456",
                    "updatedPassword456"
            );
            authService.changePassword(email, req);
        });

        // Verify state in MySQL
        User updated = userRepository.findByEmail(email).orElseThrow();
        assertThat(updated.getCredentialVersion()).isEqualTo(2L);
        assertThat(passwordEncoder.matches("updatedPassword456", updated.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("initialPassword123", updated.getPassword())).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Incorrect current password is rejected and does not mutate hash or credentialVersion")
    void incorrectCurrentPasswordRejected() {
        String email = "incorrect-pw@example.test";
        new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Test User")
                        .email(email)
                        .password(passwordEncoder.encode("correctPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    ChangePasswordRequest req = new ChangePasswordRequest(
                            "wrongPassword999",
                            "newPassword456",
                            "newPassword456"
                    );
                    authService.changePassword(email, req);
                })
        ).isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Current password is incorrect");

        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertThat(persisted.getCredentialVersion()).isEqualTo(1L);
        assertThat(passwordEncoder.matches("correctPassword123", persisted.getPassword())).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Password reuse is rejected")
    void passwordReuseRejected() {
        String email = "reuse-pw@example.test";
        new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Reuse User")
                        .email(email)
                        .password(passwordEncoder.encode("samePassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    ChangePasswordRequest req = new ChangePasswordRequest(
                            "samePassword123",
                            "samePassword123",
                            "samePassword123"
                    );
                    authService.changePassword(email, req);
                })
        ).isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("cannot be the same");

        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertThat(persisted.getCredentialVersion()).isEqualTo(1L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("BCrypt 72 UTF-8 byte limit is enforced: 72 bytes succeeds, 73 bytes is rejected")
    void bcryptByteBoundaryEnforced() {
        String email = "boundary-pw@example.test";
        new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Boundary User")
                        .email(email)
                        .password(passwordEncoder.encode("oldPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        // 72 ASCII bytes: exactly 72 UTF-8 bytes
        String valid72Bytes = "a".repeat(72);
        assertThat(valid72Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ChangePasswordRequest req = new ChangePasswordRequest(
                    "oldPassword123",
                    valid72Bytes,
                    valid72Bytes
            );
            authService.changePassword(email, req);
        });

        User userAfter72 = userRepository.findByEmail(email).orElseThrow();
        assertThat(userAfter72.getCredentialVersion()).isEqualTo(2L);
        assertThat(passwordEncoder.matches(valid72Bytes, userAfter72.getPassword())).isTrue();

        // 73 ASCII bytes: 73 UTF-8 bytes -> rejected
        String invalid73Bytes = "a".repeat(73);
        assertThat(invalid73Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);

        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    ChangePasswordRequest req = new ChangePasswordRequest(
                            valid72Bytes,
                            invalid73Bytes,
                            invalid73Bytes
                    );
                    authService.changePassword(email, req);
                })
        ).isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("72 UTF-8 bytes");

        // Multi-byte UTF-8 test: 25 characters of '€' (each 3 bytes) = 75 bytes > 72 bytes -> rejected
        String multibyte75Bytes = "€".repeat(25);
        assertThat(multibyte75Bytes.length()).isEqualTo(25); // 25 characters
        assertThat(multibyte75Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(75); // 75 bytes

        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    ChangePasswordRequest req = new ChangePasswordRequest(
                            valid72Bytes,
                            multibyte75Bytes,
                            multibyte75Bytes
                    );
                    authService.changePassword(email, req);
                })
        ).isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("72 UTF-8 bytes");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrent password change requests serialize; only one succeeds with the old password")
    void concurrentPasswordChangesSerialize() throws Exception {
        String email = "concurrent-pw@example.test";
        new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Concurrent User")
                        .email(email)
                        .password(passwordEncoder.encode("originalPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable task1 = () -> {
            try {
                startGate.await();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    ChangePasswordRequest req = new ChangePasswordRequest(
                            "originalPassword123",
                            "newPasswordOne123",
                            "newPasswordOne123"
                    );
                    authService.changePassword(email, req);
                });
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        };

        Runnable task2 = () -> {
            try {
                startGate.await();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    ChangePasswordRequest req = new ChangePasswordRequest(
                            "originalPassword123",
                            "newPasswordTwo123",
                            "newPasswordTwo123"
                    );
                    authService.changePassword(email, req);
                });
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        };

        Future<?> f1 = executor.submit(task1);
        Future<?> f2 = executor.submit(task2);

        startGate.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should succeed; the other sees changed password and fails with InvalidOperationException
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertThat(persisted.getCredentialVersion()).isEqualTo(2L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Changing password revokes any pending recovery tokens for the user in MySQL")
    void changePasswordRevokesPendingRecoveryTokens() {
        String email = "revoke-recovery@example.test";
        User user = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Revoke User")
                        .email(email)
                        .password(passwordEncoder.encode("oldPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        // Create a pending password reset token
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetTokenRepository.saveAndFlush(
                    com.featureflag.auth_service.entity.PasswordResetToken.builder()
                            .userId(user.getId())
                            .email(email)
                            .tokenHash("samplehash123456789012345678901234567890123456789012345678901234")
                            .credentialVersion(user.getCredentialVersion())
                            .expiresAt(java.time.Instant.now().plusSeconds(900))
                            .status(com.featureflag.auth_service.entity.PasswordResetStatus.PENDING)
                            .build()
            );
        });

        // Change password
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ChangePasswordRequest req = new ChangePasswordRequest(
                    "oldPassword123",
                    "newPassword456",
                    "newPassword456"
            );
            authService.changePassword(email, req);
        });

        // Check recovery tokens for user
        java.util.List<com.featureflag.auth_service.entity.PasswordResetToken> tokens =
                passwordResetTokenRepository.findAll();
        com.featureflag.auth_service.entity.PasswordResetToken token = tokens.stream()
                .filter(t -> t.getUserId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(token.getStatus()).isEqualTo(com.featureflag.auth_service.entity.PasswordResetStatus.REVOKED);
    }
}
