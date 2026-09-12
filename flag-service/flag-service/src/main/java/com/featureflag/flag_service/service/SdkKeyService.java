package com.featureflag.flag_service.service;

import com.featureflag.flag_service.exception.InvalidOperationException;

import com.featureflag.flag_service.dto.CreateSdkKeyRequest;
import com.featureflag.flag_service.dto.SdkKeyCreatedResponse;
import com.featureflag.flag_service.dto.SdkKeyMetadataResponse;
import com.featureflag.flag_service.entity.SdkKey;
import com.featureflag.flag_service.exception.ResourceNotFoundException;
import com.featureflag.flag_service.repository.SdkKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SdkKeyService {

    private static final Set<String> SUPPORTED_ENVIRONMENTS =
            Set.of("DEV", "QA", "STAGING", "PROD");

    private final SdkKeyRepository repository;
    private final SdkKeyCredentialService credentialService;
    private final Clock clock;

    @Transactional
    public SdkKeyCreatedResponse create(
            CreateSdkKeyRequest request,
            String actor
    ) {
        String environment = normalizeEnvironment(
                request.environment()
        );
        String createdBy = requireActor(actor);
        SdkKeyCredentialService.GeneratedSdkKey generated =
                credentialService.generate();
        Instant createdAt = clock.instant();

        SdkKey saved = repository.save(
                SdkKey.builder()
                        .name(request.name().trim())
                        .environment(environment)
                        .keyPrefix(generated.keyPrefix())
                        .keyHash(generated.keyHash())
                        .active(true)
                        .createdAt(createdAt)
                        .createdBy(createdBy)
                        .build()
        );

        return new SdkKeyCreatedResponse(
                saved.getId(),
                saved.getName(),
                saved.getEnvironment(),
                saved.getKeyPrefix(),
                saved.isActive(),
                saved.getCreatedAt(),
                saved.getCreatedBy(),
                generated.rawKey()
        );
    }

    @Transactional(readOnly = true)
    public Page<SdkKeyMetadataResponse> list(Pageable pageable) {
        return repository
                .findAllByOrderByCreatedAtDescIdDesc(pageable)
                .map(this::metadata);
    }

    @Transactional
    public SdkKeyMetadataResponse revoke(Long id) {
        SdkKey sdkKey = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SDK key not found with id: " + id
                ));

        if (sdkKey.isActive()) {
            sdkKey.setActive(false);
            sdkKey.setRevokedAt(clock.instant());
        }
        return metadata(sdkKey);
    }

    private SdkKeyMetadataResponse metadata(SdkKey sdkKey) {
        return new SdkKeyMetadataResponse(
                sdkKey.getId(),
                sdkKey.getName(),
                sdkKey.getEnvironment(),
                sdkKey.getKeyPrefix(),
                sdkKey.isActive(),
                sdkKey.getCreatedAt(),
                sdkKey.getRevokedAt(),
                sdkKey.getCreatedBy()
        );
    }

    private String normalizeEnvironment(String environment) {
        String normalized = environment.trim()
                .toUpperCase(Locale.ROOT);
        if (!SUPPORTED_ENVIRONMENTS.contains(normalized)) {
            throw new InvalidOperationException(
                    "Unsupported environment: " + environment
            );
        }
        return normalized;
    }

    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidOperationException(
                    "Authenticated actor is required"
            );
        }
        return actor.trim();
    }
}
