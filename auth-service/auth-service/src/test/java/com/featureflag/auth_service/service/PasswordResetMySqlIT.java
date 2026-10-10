package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.ForgotPasswordRequest;
import com.featureflag.auth_service.dto.ResetPasswordRequest;
import com.featureflag.auth_service.entity.PasswordResetStatus;
import com.featureflag.auth_service.entity.PasswordResetToken;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.exception.InvalidOperationException;
import com.featureflag.auth_service.repository.PasswordResetTokenRepository;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.client.NotificationClient;
import com.featureflag.auth_service.service.PasswordResetNotificationDispatcher;
import com.featureflag.auth_service.security.PasswordResetRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
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
class PasswordResetMySqlIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private BCryptPasswordEncoder passwordEncoder;
    private PasswordResetService passwordResetService;
    private PasswordResetNotificationDispatcher notificationDispatcher;
    private PasswordResetRateLimiter rateLimiter;
    private Clock clock;

    private static final String FRONTEND_BASE_URL = "http://localhost:3000";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // fast strength for IT
        clock = Clock.systemUTC();
        rateLimiter = mock(PasswordResetRateLimiter.class);
        org.mockito.Mockito.when(rateLimiter.isAllowed(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        notificationDispatcher = mock(PasswordResetNotificationDispatcher.class);

        passwordResetService = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                notificationDispatcher,
                rateLimiter,
                clock,
                15,
                FRONTEND_BASE_URL
        );

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                mock(com.featureflag.auth_service.security.JwtService.class),
                mock(com.featureflag.auth_service.security.LoginRateLimiter.class),
                mock(com.featureflag.auth_service.observability.AuthMetrics.class),
                passwordResetTokenRepository
        );
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Forgot password returns identical generic response for existing, nonexistent and disabled accounts")
    void genericResponseForExistingNonexistentDisabledAccounts() {
        String existingEmail = "active-user@example.test";
        String disabledEmail = "disabled-user@example.test";
        String nonexistentEmail = "nonexistent-user@example.test";

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            userRepository.saveAndFlush(User.builder()
                    .name("Active User")
                    .email(existingEmail)
                    .password(passwordEncoder.encode("InitialPass123"))
                    .role(Role.DEVELOPER)
                    .enabled(true)
                    .build());

            userRepository.saveAndFlush(User.builder()
                    .name("Disabled User")
                    .email(disabledEmail)
                    .password(passwordEncoder.encode("InitialPass123"))
                    .role(Role.DEVELOPER)
                    .enabled(false)
                    .build());
        });

        String respExisting = new TransactionTemplate(transactionManager).execute(status ->
                passwordResetService.requestPasswordReset(new ForgotPasswordRequest(existingEmail))
        );
        String respDisabled = new TransactionTemplate(transactionManager).execute(status ->
                passwordResetService.requestPasswordReset(new ForgotPasswordRequest(disabledEmail))
        );
        String respNonexistent = new TransactionTemplate(transactionManager).execute(status ->
                passwordResetService.requestPasswordReset(new ForgotPasswordRequest(nonexistentEmail))
        );

        assertThat(respExisting).isEqualTo(PasswordResetService.GENERIC_FORGOT_PASSWORD_RESPONSE);
        assertThat(respDisabled).isEqualTo(PasswordResetService.GENERIC_FORGOT_PASSWORD_RESPONSE);
        assertThat(respNonexistent).isEqualTo(PasswordResetService.GENERIC_FORGOT_PASSWORD_RESPONSE);

        // Verify tokens created only for active user
        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll().stream()
                .filter(t -> t.getEmail().equals(existingEmail))
                .toList();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getEmail()).isEqualTo(existingEmail);
        assertThat(tokens.get(0).getStatus()).isEqualTo(PasswordResetStatus.PENDING);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Reset token hash is stored in MySQL; raw token resets password, increments credentialVersion, sets status USED")
    void resetPasswordSuccess() {
        String email = "reset-success@example.test";
        User user = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Reset User")
                        .email(email)
                        .password(passwordEncoder.encode("oldPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        String rawToken = "raw-secret-token-abcdef1234567890";
        String tokenHash = sha256(rawToken);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder()
                    .userId(user.getId())
                    .email(email)
                    .tokenHash(tokenHash)
                    .credentialVersion(user.getCredentialVersion())
                    .expiresAt(Instant.now().plusSeconds(900))
                    .status(PasswordResetStatus.PENDING)
                    .build());
        });

        // Perform password reset
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetService.resetPassword(new ResetPasswordRequest(
                    rawToken,
                    "brandNewPassword123",
                    "brandNewPassword123"
            ));
        });

        // Verify in MySQL
        User updated = userRepository.findByEmail(email).orElseThrow();
        assertThat(updated.getCredentialVersion()).isEqualTo(2L);
        assertThat(passwordEncoder.matches("brandNewPassword123", updated.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("oldPassword123", updated.getPassword())).isFalse();

        PasswordResetToken updatedToken = passwordResetTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(updatedToken.getStatus()).isEqualTo(PasswordResetStatus.USED);
        assertThat(updatedToken.getUsedAt()).isNotNull();

        // Attempting to reuse the token must be rejected
        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    passwordResetService.resetPassword(new ResetPasswordRequest(
                            rawToken,
                            "anotherPassword123",
                            "anotherPassword123"
                    ));
                })
        ).isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Invalid, expired or already used reset token");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Expired token is rejected and marked EXPIRED in MySQL")
    void expiredTokenRejected() {
        String email = "expired-token@example.test";
        User user = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Expired User")
                        .email(email)
                        .password(passwordEncoder.encode("oldPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        String rawToken = "expired-raw-token-12345";
        String tokenHash = sha256(rawToken);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder()
                    .userId(user.getId())
                    .email(email)
                    .tokenHash(tokenHash)
                    .credentialVersion(user.getCredentialVersion())
                    .expiresAt(Instant.now().minusSeconds(60)) // in the past
                    .status(PasswordResetStatus.PENDING)
                    .build());
        });

        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    passwordResetService.resetPassword(new ResetPasswordRequest(
                            rawToken,
                            "brandNewPassword123",
                            "brandNewPassword123"
                    ));
                })
        ).isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Reset token has expired");

        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertThat(persisted.getCredentialVersion()).isEqualTo(1L);
        assertThat(passwordEncoder.matches("oldPassword123", persisted.getPassword())).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Resetting password revokes other pending recovery tokens for the same user in MySQL")
    void resetPasswordRevokesOtherPendingTokens() {
        String email = "multi-token@example.test";
        User user = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Multi User")
                        .email(email)
                        .password(passwordEncoder.encode("oldPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        String winnerToken = "winner-token-12345";
        String winnerHash = sha256(winnerToken);
        String loserToken = "loser-token-67890";
        String loserHash = sha256(loserToken);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder()
                    .userId(user.getId())
                    .email(email)
                    .tokenHash(winnerHash)
                    .credentialVersion(user.getCredentialVersion())
                    .expiresAt(Instant.now().plusSeconds(900))
                    .status(PasswordResetStatus.PENDING)
                    .build());

            passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder()
                    .userId(user.getId())
                    .email(email)
                    .tokenHash(loserHash)
                    .credentialVersion(user.getCredentialVersion())
                    .expiresAt(Instant.now().plusSeconds(900))
                    .status(PasswordResetStatus.PENDING)
                    .build());
        });

        // Use winner token
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetService.resetPassword(new ResetPasswordRequest(
                    winnerToken,
                    "brandNewPassword123",
                    "brandNewPassword123"
            ));
        });

        // Verify status in MySQL
        PasswordResetToken winner = passwordResetTokenRepository.findByTokenHash(winnerHash).orElseThrow();
        PasswordResetToken loser = passwordResetTokenRepository.findByTokenHash(loserHash).orElseThrow();

        assertThat(winner.getStatus()).isEqualTo(PasswordResetStatus.USED);
        assertThat(loser.getStatus()).isEqualTo(PasswordResetStatus.REVOKED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Disabled user account cannot reset password even with valid token")
    void disabledUserCannotResetPassword() {
        String email = "disabled-reset@example.test";
        User user = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Disabled User")
                        .email(email)
                        .password(passwordEncoder.encode("oldPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(false) // disabled
                        .build())
        );

        String rawToken = "disabled-user-raw-token";
        String tokenHash = sha256(rawToken);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder()
                    .userId(user.getId())
                    .email(email)
                    .tokenHash(tokenHash)
                    .credentialVersion(user.getCredentialVersion())
                    .expiresAt(Instant.now().plusSeconds(900))
                    .status(PasswordResetStatus.PENDING)
                    .build());
        });

        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    passwordResetService.resetPassword(new ResetPasswordRequest(
                            rawToken,
                            "brandNewPassword123",
                            "brandNewPassword123"
                    ));
                })
        ).isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("User account is disabled");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Password reuse against current password is rejected during reset")
    void passwordReuseRejected() {
        String email = "reuse-test@example.test";
        User user = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Reuse User")
                        .email(email)
                        .password(passwordEncoder.encode("currentPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        String rawToken = "reuse-test-token";
        String tokenHash = sha256(rawToken);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder()
                    .userId(user.getId())
                    .email(email)
                    .tokenHash(tokenHash)
                    .credentialVersion(user.getCredentialVersion())
                    .expiresAt(Instant.now().plusSeconds(900))
                    .status(PasswordResetStatus.PENDING)
                    .build());
        });

        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    passwordResetService.resetPassword(new ResetPasswordRequest(
                            rawToken,
                            "currentPassword123",
                            "currentPassword123"
                    ));
                })
        ).isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("New password cannot be the same as current password");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrent reset attempts with the same token result in exactly one winner and one conflict/failure")
    void concurrentResetAttemptsOneWinner() throws Exception {
        String email = "concurrent-reset@example.test";
        User user = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Concurrent User")
                        .email(email)
                        .password(passwordEncoder.encode("initialPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        String rawToken = "concurrent-raw-token-777";
        String tokenHash = sha256(rawToken);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder()
                    .userId(user.getId())
                    .email(email)
                    .tokenHash(tokenHash)
                    .credentialVersion(user.getCredentialVersion())
                    .expiresAt(Instant.now().plusSeconds(900))
                    .status(PasswordResetStatus.PENDING)
                    .build());
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable task1 = () -> {
            try {
                startGate.await();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    passwordResetService.resetPassword(new ResetPasswordRequest(
                            rawToken,
                            "passwordFromTaskOne1",
                            "passwordFromTaskOne1"
                    ));
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
                    passwordResetService.resetPassword(new ResetPasswordRequest(
                            rawToken,
                            "passwordFromTaskTwo2",
                            "passwordFromTaskTwo2"
                    ));
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

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertThat(persisted.getCredentialVersion()).isEqualTo(2L);

        PasswordResetToken updatedToken = passwordResetTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(updatedToken.getStatus()).isEqualTo(PasswordResetStatus.USED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrent reset attempts with TWO DIFFERENT tokens for the same account: only one succeeds, the other cannot overwrite")
    void concurrentTwoDifferentResetTokensOneWinner() throws Exception {
        String email = "concurrent-diff-tokens@example.test";
        User user = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Multi-Token User")
                        .email(email)
                        .password(passwordEncoder.encode("initialPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        String token1 = "diff-token-1-abc1234567890123456789";
        String hash1 = sha256(token1);
        String token2 = "diff-token-2-def1234567890123456789";
        String hash2 = sha256(token2);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder()
                    .userId(user.getId())
                    .email(email)
                    .tokenHash(hash1)
                    .credentialVersion(user.getCredentialVersion())
                    .expiresAt(Instant.now().plusSeconds(900))
                    .status(PasswordResetStatus.PENDING)
                    .build());

            passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder()
                    .userId(user.getId())
                    .email(email)
                    .tokenHash(hash2)
                    .credentialVersion(user.getCredentialVersion())
                    .expiresAt(Instant.now().plusSeconds(900))
                    .status(PasswordResetStatus.PENDING)
                    .build());
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable task1 = () -> {
            try {
                startGate.await();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    passwordResetService.resetPassword(new ResetPasswordRequest(
                            token1,
                            "winnerPasswordOne1",
                            "winnerPasswordOne1"
                    ));
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
                    passwordResetService.resetPassword(new ResetPasswordRequest(
                            token2,
                            "winnerPasswordTwo2",
                            "winnerPasswordTwo2"
                    ));
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

        // Exactly one reset succeeds; the other is rejected because credentialVersion changed or token was revoked
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertThat(persisted.getCredentialVersion()).isEqualTo(2L);

        // One token is USED, the other is REVOKED
        PasswordResetToken t1 = passwordResetTokenRepository.findByTokenHash(hash1).orElseThrow();
        PasswordResetToken t2 = passwordResetTokenRepository.findByTokenHash(hash2).orElseThrow();

        assertThat(List.of(t1.getStatus(), t2.getStatus()))
                .containsExactlyInAnyOrder(PasswordResetStatus.USED, PasswordResetStatus.REVOKED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrent password change versus reset acceptance: serializes cleanly without deadlock or credential overwrites")
    void concurrentChangePasswordVersusResetAcceptance() throws Exception {
        String email = "change-vs-reset@example.test";
        User user = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Race User")
                        .email(email)
                        .password(passwordEncoder.encode("originalPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        String rawToken = "race-reset-token-123456789012345678";
        String tokenHash = sha256(rawToken);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder()
                    .userId(user.getId())
                    .email(email)
                    .tokenHash(tokenHash)
                    .credentialVersion(user.getCredentialVersion())
                    .expiresAt(Instant.now().plusSeconds(900))
                    .status(PasswordResetStatus.PENDING)
                    .build());
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable changeTask = () -> {
            try {
                startGate.await();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    authService.changePassword(
                            email,
                            new com.featureflag.auth_service.dto.ChangePasswordRequest(
                                    "originalPassword123",
                                    "newViaChangePass1",
                                    "newViaChangePass1"
                            )
                    );
                });
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        };

        Runnable resetTask = () -> {
            try {
                startGate.await();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    passwordResetService.resetPassword(new ResetPasswordRequest(
                            rawToken,
                            "newViaResetPass2",
                            "newViaResetPass2"
                    ));
                });
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        };

        Future<?> f1 = executor.submit(changeTask);
        Future<?> f2 = executor.submit(resetTask);

        startGate.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one succeeds; the second operation sees the new credentialVersion or revoked token
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertThat(persisted.getCredentialVersion()).isEqualTo(2L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Concurrent reset issuance versus password change: serializes cleanly under User lock")
    void concurrentResetIssuanceVersusPasswordChange() throws Exception {
        String email = "issue-vs-change@example.test";
        User user = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Issuance Race User")
                        .email(email)
                        .password(passwordEncoder.encode("originalPassword123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger issuanceSuccess = new AtomicInteger(0);
        AtomicInteger changeSuccess = new AtomicInteger(0);

        Runnable issueTask = () -> {
            try {
                startGate.await();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    passwordResetService.requestPasswordReset(new ForgotPasswordRequest(email));
                });
                issuanceSuccess.incrementAndGet();
            } catch (Exception e) {
                // Should not happen as issuance is guarded
            }
        };

        Runnable changeTask = () -> {
            try {
                startGate.await();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    authService.changePassword(
                            email,
                            new com.featureflag.auth_service.dto.ChangePasswordRequest(
                                    "originalPassword123",
                                    "newPasswordAfterIssue1",
                                    "newPasswordAfterIssue1"
                            )
                    );
                });
                changeSuccess.incrementAndGet();
            } catch (Exception e) {
                // If change task ran second, it succeeds; if first, it also succeeds
            }
        };

        Future<?> f1 = executor.submit(issueTask);
        Future<?> f2 = executor.submit(changeTask);

        startGate.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(issuanceSuccess.get()).isEqualTo(1);
        assertThat(changeSuccess.get()).isEqualTo(1);

        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertThat(persisted.getCredentialVersion()).isEqualTo(2L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Deleted and recreated account with the same email CANNOT consume the old recovery token")
    void deletedAndRecreatedAccountCannotConsumeOldToken() {
        String email = "recreated-account@example.test";

        // Step 1: Original account
        User originalUser = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.saveAndFlush(User.builder()
                        .name("Original User")
                        .email(email)
                        .password(passwordEncoder.encode("originalPass123"))
                        .role(Role.DEVELOPER)
                        .enabled(true)
                        .build())
        );

        String rawToken = "recreated-account-raw-token-123456";
        String tokenHash = sha256(rawToken);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            passwordResetTokenRepository.saveAndFlush(PasswordResetToken.builder()
                    .userId(originalUser.getId()) // bound to original userId
                    .email(email)
                    .tokenHash(tokenHash)
                    .credentialVersion(originalUser.getCredentialVersion())
                    .expiresAt(Instant.now().plusSeconds(900))
                    .status(PasswordResetStatus.PENDING)
                    .build());
        });

        // Step 2: "Delete" original account and re-create a new account with the same email
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            userRepository.deleteById(originalUser.getId());
            userRepository.flush();

            userRepository.saveAndFlush(User.builder()
                    .name("New User Same Email")
                    .email(email)
                    .password(passwordEncoder.encode("brandNewAccountPass123"))
                    .role(Role.DEVELOPER)
                    .enabled(true)
                    .build());
        });

        // Step 3: Attempting to reset password using the old token must fail because immutable userId does not match
        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    passwordResetService.resetPassword(new ResetPasswordRequest(
                            rawToken,
                            "attackerChosenPassword1",
                            "attackerChosenPassword1"
                    ));
                })
        ).isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("User account no longer exists");
    }
}
