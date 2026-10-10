package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.AuthResponse;
import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.observability.AuthMetrics;
import com.featureflag.auth_service.security.JwtService;
import com.featureflag.auth_service.security.LoginRateLimiter;
import com.featureflag.auth_service.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.featureflag.auth_service.dto.ChangePasswordRequest;
import com.featureflag.auth_service.dto.UserAccountStatusResponse;
import com.featureflag.auth_service.exception.InvalidOperationException;
import com.featureflag.auth_service.exception.ResourceNotFoundException;
import com.featureflag.auth_service.validation.BcryptPasswordValidator;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuthMetrics authMetrics;
    private final com.featureflag.auth_service.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter,
            AuthMetrics authMetrics,
            com.featureflag.auth_service.repository.PasswordResetTokenRepository passwordResetTokenRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.authMetrics = authMetrics;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter,
            AuthMetrics authMetrics
    ) {
        this(userRepository, passwordEncoder, jwtService, loginRateLimiter, authMetrics, null);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.getEmail());

        loginRateLimiter.checkAllowed(normalizedEmail);

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        boolean passwordMatches = user != null
                && passwordEncoder.matches(
                        request.getPassword(),
                        user.getPassword()
                );

        if (user == null
                || !passwordMatches
                || !user.isEnabled()) {
            loginRateLimiter.recordFailure(normalizedEmail);
            authMetrics.loginFailed();
            throw new BadCredentialsException("Invalid email or password");
        }

        String canonicalEmail = EmailNormalizer.normalize(user.getEmail());
        String token = jwtService.generateToken(
                user.getId(),
                canonicalEmail,
                user.getRole().name(),
                user.getCredentialVersion() != null ? user.getCredentialVersion() : 1L
        );

        loginRateLimiter.recordSuccess(normalizedEmail);
        authMetrics.loginSucceeded();

        return new AuthResponse(
                token,
                canonicalEmail,
                user.getRole().name()
        );
    }

    public List<String> getNotificationRecipients(List<String> roleNames) {
        List<Role> targetRoles = new ArrayList<>();

        if (roleNames != null && !roleNames.isEmpty()) {
            for (String r : roleNames) {
                try {
                    targetRoles.add(Role.valueOf(r.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown role requested for notification recipient resolution: {}", r);
                }
            }
        }

        if (targetRoles.isEmpty()) {
            targetRoles = List.of(Role.OWNER, Role.ADMIN);
        }

        List<User> users = userRepository.findByRoleIn(targetRoles);

        return users.stream()
                .filter(User::isEnabled)
                .map(User::getEmail)
                .filter(Objects::nonNull)
                .map(EmailNormalizer::normalize)
                .filter(email -> !email.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    public UserAccountStatusResponse getUserAccountStatus(String email, Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID is required and must be positive");
        }
        String normalizedEmail = EmailNormalizer.normalize(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found"
                ));

        if (!userId.equals(user.getId())) {
            throw new ResourceNotFoundException(
                    "User identity mismatch"
            );
        }

        return new UserAccountStatusResponse(
                user.getId(),
                EmailNormalizer.normalize(user.getEmail()),
                user.getRole(),
                user.isEnabled(),
                user.getCredentialVersion() != null ? user.getCredentialVersion() : 1L
        );
    }

    @Transactional
    public void changePassword(String email, Long callerUserId, Long callerTokenVersion, ChangePasswordRequest request) {
        if (request == null) {
            throw new InvalidOperationException("Change password request cannot be null");
        }
        if (request.currentPassword() == null || request.currentPassword().isBlank()) {
            throw new InvalidOperationException("Current password is required");
        }
        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new InvalidOperationException("New password is required");
        }
        if (request.confirmPassword() != null && !request.confirmPassword().isBlank()
                && !request.newPassword().equals(request.confirmPassword())) {
            throw new InvalidOperationException("New password and confirm password do not match");
        }
        if (request.newPassword().length() < 8) {
            throw new InvalidOperationException("New password must be at least 8 characters long");
        }
        if (!BcryptPasswordValidator.withinByteLimit(request.newPassword())) {
            throw new InvalidOperationException("New password must not exceed 72 UTF-8 bytes");
        }

        String normalizedEmail = EmailNormalizer.normalize(email);
        User user = userRepository.findByEmailForUpdate(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (callerUserId != null && !callerUserId.equals(user.getId())) {
            throw new InvalidOperationException("Authenticated user ID mismatch");
        }

        long currentVersion = user.getCredentialVersion() != null ? user.getCredentialVersion() : 1L;
        if (callerTokenVersion != null && !callerTokenVersion.equals(currentVersion)) {
            throw new InvalidOperationException("Stale session credential version");
        }

        if (!user.isEnabled()) {
            throw new InvalidOperationException("User account is disabled");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidOperationException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new InvalidOperationException("New password cannot be the same as current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setCredentialVersion(currentVersion + 1L);
        userRepository.save(user);

        // Revoke any pending password recovery tokens for this user
        if (passwordResetTokenRepository != null) {
            java.util.List<com.featureflag.auth_service.entity.PasswordResetToken> pendingTokens =
                    passwordResetTokenRepository.findByUserIdAndStatusForUpdate(user.getId(), com.featureflag.auth_service.entity.PasswordResetStatus.PENDING);
            for (com.featureflag.auth_service.entity.PasswordResetToken pendingToken : pendingTokens) {
                pendingToken.setStatus(com.featureflag.auth_service.entity.PasswordResetStatus.REVOKED);
                passwordResetTokenRepository.save(pendingToken);
            }
        }
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        changePassword(email, null, null, request);
    }
}
