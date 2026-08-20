package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.AuthResponse;
import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.security.JwtService;
import com.featureflag.auth_service.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.getEmail());

        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String canonicalEmail = EmailNormalizer.normalize(user.getEmail());
        String token = jwtService.generateToken(
                canonicalEmail,
                user.getRole().name()
        );

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
}
