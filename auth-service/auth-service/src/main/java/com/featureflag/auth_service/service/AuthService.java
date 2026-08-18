package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.AuthResponse;
import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.dto.RegisterRequest;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public String register(RegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException(
                    "User already exists with email: "
                            + request.getEmail()
            );
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(
                        passwordEncoder.encode(
                                request.getPassword()
                        )
                )
                .role(Role.VIEWER)
                .build();

        userRepository.save(user);

        return "User Registered Successfully as VIEWER";
    }

    public AuthResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new RuntimeException(
                    "Invalid password"
            );
        }

        String token =
                jwtService.generateToken(
                        user.getEmail(),
                        user.getRole().name()
                );

        return new AuthResponse(
                token,
                user.getEmail(),
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
                .map(String::trim)
                .filter(email -> !email.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }
}
