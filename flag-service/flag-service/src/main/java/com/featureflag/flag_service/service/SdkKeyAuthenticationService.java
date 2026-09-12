package com.featureflag.flag_service.service;

import com.featureflag.flag_service.repository.SdkKeyRepository;
import com.featureflag.flag_service.security.SdkKeyPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SdkKeyAuthenticationService {

    private final SdkKeyRepository repository;
    private final SdkKeyCredentialService credentialService;

    @Transactional(readOnly = true)
    public Optional<SdkKeyPrincipal> authenticate(String rawKey) {
        if (!credentialService.hasValidFormat(rawKey)) {
            return Optional.empty();
        }

        return repository
                .findByKeyHashAndActiveTrue(
                        credentialService.hash(rawKey)
                )
                .map(sdkKey -> new SdkKeyPrincipal(
                        sdkKey.getId(),
                        sdkKey.getName(),
                        sdkKey.getEnvironment(),
                        sdkKey.getKeyPrefix()
                ));
    }
}
