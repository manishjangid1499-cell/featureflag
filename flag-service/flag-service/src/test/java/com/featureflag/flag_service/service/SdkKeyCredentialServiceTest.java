package com.featureflag.flag_service.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SdkKeyCredentialServiceTest {

    private final SdkKeyCredentialService service =
            new SdkKeyCredentialService();

    @Test
    void generatedCredentialUsesFull256BitsAndUrlSafeFormat() {
        var first = service.generate();
        var second = service.generate();

        assertThat(first.rawKey())
                .startsWith(SdkKeyCredentialService.KEY_PREFIX)
                .hasSize(
                        SdkKeyCredentialService.KEY_PREFIX.length()
                                + SdkKeyCredentialService
                                .ENCODED_SECRET_LENGTH
                )
                .isNotEqualTo(second.rawKey());
        assertThat(service.hasValidFormat(first.rawKey())).isTrue();

        String encoded = first.rawKey().substring(
                SdkKeyCredentialService.KEY_PREFIX.length()
        );
        assertThat(Base64.getUrlDecoder().decode(encoded))
                .hasSize(SdkKeyCredentialService.SECRET_BYTES);
        assertThat(first.keyHash())
                .hasSize(64)
                .isEqualTo(service.hash(first.rawKey()));
        assertThat(first.keyPrefix())
                .isEqualTo(first.rawKey().substring(0, 15));
    }

    @Test
    void malformedCredentialsAreRejectedBeforeHashLookup() {
        assertThat(service.hasValidFormat(null)).isFalse();
        assertThat(service.hasValidFormat("")).isFalse();
        assertThat(service.hasValidFormat("ff_sdk_short")).isFalse();
        assertThat(service.hasValidFormat(
                "other_" + "A".repeat(44)
        )).isFalse();
        assertThat(service.hasValidFormat(
                "ff_sdk_" + "A".repeat(42)
        )).isFalse();
        assertThat(service.hasValidFormat(
                "ff_sdk_" + "A".repeat(44)
        )).isFalse();
        assertThat(service.hasValidFormat(
                "ff_sdk_" + "!".repeat(43)
        )).isFalse();
        assertThat(service.hasValidFormat(
                "ff_sdk_" + "é".repeat(43)
        )).isFalse();
    }

    @Test
    void base64UrlAlphabetIsAccepted() {
        assertThat(service.hasValidFormat(
                "ff_sdk_" + "A".repeat(41) + "-_"
        )).isTrue();
    }
}
