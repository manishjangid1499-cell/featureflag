package com.featureflag.auth_service.config;

import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OwnerBootstrapService implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OwnerBootstrapProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        if (!properties.isEnabled()) {
            log.info("Initial OWNER bootstrap is disabled.");
            return;
        }

        if (userRepository.existsByRole(Role.OWNER)) {
            log.info("OWNER already exists. Bootstrap skipped.");
            return;
        }

        validateConfiguration();

        User owner = User.builder()
                .name(properties.getName().trim())
                .email(properties.getEmail().trim().toLowerCase())
                .password(
                        passwordEncoder.encode(
                                properties.getPassword()
                        )
                )
                .role(Role.OWNER)
                .build();

        userRepository.save(owner);

        log.info(
                "Initial OWNER account created successfully."
        );
    }

    private void validateConfiguration() {

        if (properties.getName() == null
                || properties.getName().isBlank()) {

            throw new IllegalStateException(
                    "Bootstrap OWNER name is not configured."
            );
        }

        if (properties.getEmail() == null
                || properties.getEmail().isBlank()) {

            throw new IllegalStateException(
                    "Bootstrap OWNER email is not configured."
            );
        }

        if (properties.getPassword() == null
                || properties.getPassword().isBlank()) {

            throw new IllegalStateException(
                    "Bootstrap OWNER password is not configured."
            );
        }

        if (properties.getPassword().length() < 8) {

            throw new IllegalStateException(
                    "Bootstrap OWNER password must be at least 8 characters."
            );
        }
    }
}