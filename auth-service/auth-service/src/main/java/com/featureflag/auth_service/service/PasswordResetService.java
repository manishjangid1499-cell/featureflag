package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.ForgotPasswordRequest;
import com.featureflag.auth_service.dto.PasswordResetNotificationDto;
import com.featureflag.auth_service.dto.ResetPasswordRequest;
import com.featureflag.auth_service.entity.PasswordResetStatus;
import com.featureflag.auth_service.entity.PasswordResetToken;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.exception.InvalidOperationException;
import com.featureflag.auth_service.repository.PasswordResetTokenRepository;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.security.PasswordResetRateLimiter;
import com.featureflag.auth_service.util.EmailNormalizer;
import com.featureflag.auth_service.validation.BcryptPasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    public static final String GENERIC_FORGOT_PASSWORD_RESPONSE =
            "If an eligible account exists, you will receive password reset instructions.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetNotificationDispatcher passwordResetNotificationDispatcher;
    private final PasswordResetRateLimiter passwordResetRateLimiter;
    private final Clock clock;

    @Value("${app.password-reset.expiration-minutes:15}")
    private int expirationMinutes = 15;

    @Value("${app.password-reset.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl = "http://localhost:5173";

    @org.springframework.beans.factory.annotation.Autowired
    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetNotificationDispatcher passwordResetNotificationDispatcher,
            PasswordResetRateLimiter passwordResetRateLimiter,
            Clock clock,
            @Value("${app.password-reset.expiration-minutes:15}") int expirationMinutes,
            @Value("${app.password-reset.frontend-base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetNotificationDispatcher = passwordResetNotificationDispatcher;
        this.passwordResetRateLimiter = passwordResetRateLimiter;
        this.clock = clock;
        this.expirationMinutes = expirationMinutes;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public String requestPasswordReset(ForgotPasswordRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            return GENERIC_FORGOT_PASSWORD_RESPONSE;
        }

        String normalizedEmail = EmailNormalizer.normalize(request.email());

        // Bounded by rate limiter
        if (!passwordResetRateLimiter.isAllowed(normalizedEmail)) {
            log.warn("Password reset request rate-limited for email hash");
            return GENERIC_FORGOT_PASSWORD_RESPONSE;
        }
        passwordResetRateLimiter.recordRequest(normalizedEmail);

        Optional<User> userOptional = userRepository.findByEmailForUpdate(normalizedEmail);
        if (userOptional.isEmpty()) {
            log.info("Password reset requested for nonexistent email");
            return GENERIC_FORGOT_PASSWORD_RESPONSE;
        }

        User user = userOptional.get();
        if (!user.isEnabled()) {
            log.info("Password reset requested for disabled user");
            return GENERIC_FORGOT_PASSWORD_RESPONSE;
        }

        // Revoke any previous pending recovery tokens for this user
        List<PasswordResetToken> existingTokens =
                passwordResetTokenRepository.findByUserIdAndStatusForUpdate(user.getId(), PasswordResetStatus.PENDING);
        for (PasswordResetToken existing : existingTokens) {
            existing.setStatus(PasswordResetStatus.REVOKED);
            passwordResetTokenRepository.save(existing);
        }

        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(expirationMinutes * 60L);
        long currentVersion = user.getCredentialVersion() != null ? user.getCredentialVersion() : 1L;

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .tokenHash(tokenHash)
                .credentialVersion(currentVersion)
                .expiresAt(expiresAt)
                .status(PasswordResetStatus.PENDING)
                .createdAt(now)
                .build();

        passwordResetTokenRepository.save(resetToken);

        String resetUrl = frontendBaseUrl + "/reset-password?token=" + rawToken;

        PasswordResetNotificationDto notificationDto = PasswordResetNotificationDto.builder()
                .recipient(user.getEmail())
                .userName(user.getName() != null && !user.getName().isBlank() ? user.getName().trim() : "")
                .expirationMinutes(expirationMinutes)
                .resetUrl(resetUrl)
                .build();

        // Dispatch notification after transaction commit
        passwordResetNotificationDispatcher.dispatchAfterCommit(notificationDto);

        return GENERIC_FORGOT_PASSWORD_RESPONSE;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request == null) {
            throw new InvalidOperationException("Reset password request cannot be null");
        }
        if (request.token() == null || request.token().isBlank()) {
            throw new InvalidOperationException("Reset token is required");
        }
        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new InvalidOperationException("New password is required");
        }
        if (request.confirmPassword() == null || !request.newPassword().equals(request.confirmPassword())) {
            throw new InvalidOperationException("New password and confirm password do not match");
        }
        if (request.newPassword().length() < 8) {
            throw new InvalidOperationException("New password must be at least 8 characters long");
        }
        if (!BcryptPasswordValidator.withinByteLimit(request.newPassword())) {
            throw new InvalidOperationException("New password must not exceed 72 UTF-8 bytes");
        }

        String tokenHash = hashToken(request.token().trim());

        // Step 1: Read token without lock first to identify the account (userId)
        PasswordResetToken initialToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidOperationException("Invalid, expired or already used reset token"));

        Long tokenUserId = initialToken.getUserId();
        if (tokenUserId == null) {
            throw new InvalidOperationException("Invalid reset token: missing account reference");
        }

        // Step 2: Acquire authoritative PESSIMISTIC_WRITE lock on User first (consistent lock ordering: User -> Tokens)
        User user = userRepository.findByIdForUpdate(tokenUserId)
                .orElseThrow(() -> new InvalidOperationException("User account no longer exists"));

        // Step 3: Acquire authoritative PESSIMISTIC_WRITE lock on the reset token
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvalidOperationException("Invalid, expired or already used reset token"));

        // Step 4: Strict validation under lock: verify token still belongs to this exact user
        if (!user.getId().equals(token.getUserId())) {
            throw new InvalidOperationException("Reset token was not issued for this user account");
        }

        Instant now = clock.instant();

        // Step 5: Recheck status under lock
        if (token.getStatus() != PasswordResetStatus.PENDING) {
            throw new InvalidOperationException("Invalid, expired or already used reset token");
        }

        // Step 6: Recheck expiry under lock
        if (now.isAfter(token.getExpiresAt())) {
            token.setStatus(PasswordResetStatus.EXPIRED);
            passwordResetTokenRepository.save(token);
            throw new InvalidOperationException("Reset token has expired. Please request a new password reset.");
        }

        if (!user.isEnabled()) {
            token.setStatus(PasswordResetStatus.REVOKED);
            passwordResetTokenRepository.save(token);
            throw new InvalidOperationException("User account is disabled");
        }

        long currentVersion = user.getCredentialVersion() != null ? user.getCredentialVersion() : 1L;
        if (!token.getCredentialVersion().equals(currentVersion)) {
            token.setStatus(PasswordResetStatus.REVOKED);
            passwordResetTokenRepository.save(token);
            throw new InvalidOperationException("Password reset link is no longer valid due to a prior security change");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new InvalidOperationException("New password cannot be the same as current password");
        }

        // Update password and increment credentialVersion
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setCredentialVersion(currentVersion + 1L);
        userRepository.save(user);

        // Mark token as USED
        token.setStatus(PasswordResetStatus.USED);
        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);

        // Revoke any other pending recovery tokens for this user
        List<PasswordResetToken> otherTokens =
                passwordResetTokenRepository.findByUserIdAndStatusForUpdate(user.getId(), PasswordResetStatus.PENDING);
        for (PasswordResetToken other : otherTokens) {
            other.setStatus(PasswordResetStatus.REVOKED);
            passwordResetTokenRepository.save(other);
        }

        log.info("Password reset successfully completed for user");
    }

    private String generateSecureToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return TOKEN_ENCODER.encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }
}
