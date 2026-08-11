package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.AuthResponse;
import com.featureflag.auth_service.dto.LoginRequest;
import com.featureflag.auth_service.dto.RegisterRequest;
import com.featureflag.auth_service.dto.TokenValidationResponse;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
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
    public TokenValidationResponse validateToken(String token) {

        try {

            String email = jwtService.extractEmail(token);

            if (!jwtService.isTokenValid(token)) {
                return new TokenValidationResponse(
                        false,
                        null,
                        null
                );
            }

            User user = userRepository.findByEmail(email)
                    .orElse(null);

            if (user == null) {
                return new TokenValidationResponse(
                        false,
                        null,
                        null
                );
            }

            return new TokenValidationResponse(
                    true,
                    user.getEmail(),
                    user.getRole().name()
            );

        } catch (Exception e) {

            return new TokenValidationResponse(
                    false,
                    null,
                    null
            );
        }
    }
}