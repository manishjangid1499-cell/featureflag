package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.SdkKey;
import com.featureflag.flag_service.repository.SdkKeyRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SdkKeyAuthenticationServiceTest {

    private final SdkKeyRepository repository =
            mock(SdkKeyRepository.class);
    private final SdkKeyCredentialService credentials =
            new SdkKeyCredentialService();
    private final SdkKeyAuthenticationService service =
            new SdkKeyAuthenticationService(repository, credentials);

    @Test
    void activeCredentialAuthenticatesWithStoredEnvironment() {
        var generated = credentials.generate();
        SdkKey sdkKey = SdkKey.builder()
                .id(5L)
                .name("Development app")
                .environment("DEV")
                .keyPrefix(generated.keyPrefix())
                .keyHash(generated.keyHash())
                .active(true)
                .build();
        when(repository.findByKeyHashAndActiveTrue(
                generated.keyHash()
        )).thenReturn(Optional.of(sdkKey));

        var principal = service.authenticate(
                generated.rawKey()
        ).orElseThrow();

        assertThat(principal.id()).isEqualTo(5L);
        assertThat(principal.environment()).isEqualTo("DEV");
        assertThat(principal.keyPrefix())
                .isEqualTo(generated.keyPrefix());
    }

    @Test
    void malformedCredentialNeverQueriesDatabase() {
        assertThat(service.authenticate("invalid")).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void absentOrRevokedCredentialIsRejectedGenerically() {
        var generated = credentials.generate();
        when(repository.findByKeyHashAndActiveTrue(
                generated.keyHash()
        )).thenReturn(Optional.empty());

        assertThat(service.authenticate(generated.rawKey())).isEmpty();
    }
}
