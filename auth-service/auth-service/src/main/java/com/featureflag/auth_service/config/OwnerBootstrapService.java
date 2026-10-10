package com.featureflag.auth_service.config;

import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.util.EmailNormalizer;
import com.featureflag.auth_service.validation.BcryptPasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.featureflag.auth_service.entity.SystemInvariant;
import com.featureflag.auth_service.repository.SystemInvariantRepository;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OwnerBootstrapService implements ApplicationRunner {

    private final UserRepository userRepository;
    private final SystemInvariantRepository systemInvariantRepository;
    private final PasswordEncoder passwordEncoder;
    private final OwnerBootstrapProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        if (!properties.isEnabled()) {
            log.info("Initial OWNER bootstrap is disabled.");
            return;
        }

        // Acquire cluster-wide pessimistic lock on the installation owner singleton row
        Optional<SystemInvariant> lockOpt = systemInvariantRepository.findByNameForUpdate(SystemInvariant.OWNER_INSTALLATION);
        SystemInvariant invariant = lockOpt.orElseGet(() -> {
            SystemInvariant newInv = SystemInvariant.builder()
                    .name(SystemInvariant.OWNER_INSTALLATION)
                    .updatedAt(Instant.now())
                    .build();
            return systemInvariantRepository.save(newInv);
        });

        long ownerCount = userRepository.countByRole(Role.OWNER);
        if (ownerCount > 1) {
            log.error("Multiple OWNER accounts ({}) detected in database. Inconsistent state; bootstrap aborted without mutating.", ownerCount);
            throw new IllegalStateException("Multiple OWNER accounts detected in database. Inconsistent state; bootstrap aborted without mutating.");
        }

        if (ownerCount == 1) {
            log.info("OWNER already exists. Bootstrap skipped.");
            if (invariant.getOwnerUserId() == null) {
                userRepository.findAllByRole(Role.OWNER).stream().findFirst().ifPresent(owner -> {
                    invariant.setOwnerUserId(owner.getId());
                    invariant.setUpdatedAt(Instant.now());
                    systemInvariantRepository.save(invariant);
                });
            }
            return;
        }

        validateConfiguration();

        String normalizedEmail = EmailNormalizer.normalize(properties.getEmail());
        Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
        if (existingUser.isPresent()) {
            log.warn("Account with bootstrap email {} already exists with role {}. Bootstrap aborted to prevent hijacking.",
                    normalizedEmail, existingUser.get().getRole());
            return;
        }

        User owner = User.builder()
                .name(properties.getName().trim())
                .email(normalizedEmail)
                .password(
                        passwordEncoder.encode(
                                properties.getPassword()
                        )
                )
                .role(Role.OWNER)
                .build();

        User savedOwner = userRepository.save(owner);
        invariant.setOwnerUserId(savedOwner.getId());
        invariant.setUpdatedAt(Instant.now());
        systemInvariantRepository.save(invariant);

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
        if (!BcryptPasswordValidator.withinByteLimit(properties.getPassword())) {
            throw new IllegalStateException("Bootstrap OWNER password must not exceed 72 UTF-8 bytes.");
        }
    }
}
