package com.featureflag.flag_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.CreateSdkKeyRequest;
import com.featureflag.flag_service.entity.SdkKey;
import com.featureflag.flag_service.repository.SdkKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SdkKeyServiceTest {

    private static final String RAW_KEY =
            "ff_sdk_" + "A".repeat(43);
    private static final String HASH = "b".repeat(64);

    @Mock
    private SdkKeyRepository repository;

    @Mock
    private SdkKeyCredentialService credentialService;

    private SdkKeyService service;

    @BeforeEach
    void setUp() {
        service = new SdkKeyService(repository, credentialService);
    }

    @Test
    void createReturnsRawKeyOnceButPersistsOnlyHash() {
        when(credentialService.generate()).thenReturn(
                new SdkKeyCredentialService.GeneratedSdkKey(
                        RAW_KEY,
                        HASH,
                        "ff_sdk_AAAAAAAA"
                )
        );
        when(repository.save(any(SdkKey.class)))
                .thenAnswer(invocation -> {
                    SdkKey sdkKey = invocation.getArgument(0);
                    sdkKey.setId(10L);
                    return sdkKey;
                });

        var response = service.create(
                new CreateSdkKeyRequest(
                        " Production backend ",
                        " prod "
                ),
                " owner@example.com "
        );

        assertThat(response.rawKey()).isEqualTo(RAW_KEY);
        assertThat(response.environment()).isEqualTo("PROD");
        assertThat(response.createdBy())
                .isEqualTo("owner@example.com");

        ArgumentCaptor<SdkKey> captor =
                ArgumentCaptor.forClass(SdkKey.class);
        verify(repository).save(captor.capture());
        SdkKey persisted = captor.getValue();
        assertThat(persisted.getKeyHash()).isEqualTo(HASH);
        assertThat(persisted.getKeyHash()).isNotEqualTo(RAW_KEY);
        assertThat(persisted.getKeyPrefix())
                .isEqualTo("ff_sdk_AAAAAAAA");
        assertThat(persisted.getName())
                .isEqualTo("Production backend");
        assertThat(persisted.isActive()).isTrue();
        assertThat(SdkKey.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("rawKey");
    }

    @Test
    void listSerializesOnlySafeMetadata() throws Exception {
        when(repository.findAllByOrderByCreatedAtDescIdDesc())
                .thenReturn(List.of(sdkKey(true)));

        String json = new ObjectMapper()
                .findAndRegisterModules()
                .writeValueAsString(service.list());

        assertThat(json)
                .contains("ff_sdk_AAAAAAAA")
                .doesNotContain("rawKey", "keyHash", HASH, RAW_KEY);
    }

    @Test
    void revokeIsLogicalAndIdempotent() {
        SdkKey sdkKey = sdkKey(true);
        when(repository.findById(10L)).thenReturn(
                java.util.Optional.of(sdkKey)
        );

        var first = service.revoke(10L);
        LocalDateTime revokedAt = sdkKey.getRevokedAt();
        var second = service.revoke(10L);

        assertThat(first.active()).isFalse();
        assertThat(second.active()).isFalse();
        assertThat(revokedAt).isNotNull();
        assertThat(sdkKey.getRevokedAt()).isEqualTo(revokedAt);
    }

    @Test
    void unsupportedEnvironmentIsRejected() {
        assertThatThrownBy(() -> service.create(
                new CreateSdkKeyRequest("Backend", "UNKNOWN"),
                "owner@example.com"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private SdkKey sdkKey(boolean active) {
        return SdkKey.builder()
                .id(10L)
                .name("Production backend")
                .environment("PROD")
                .keyPrefix("ff_sdk_AAAAAAAA")
                .keyHash(HASH)
                .active(active)
                .createdAt(LocalDateTime.parse(
                        "2026-08-27T12:00:00"
                ))
                .createdBy("owner@example.com")
                .build();
    }
}
