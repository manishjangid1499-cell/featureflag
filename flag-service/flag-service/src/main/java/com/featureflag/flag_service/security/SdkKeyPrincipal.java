package com.featureflag.flag_service.security;

public record SdkKeyPrincipal(
        Long id,
        String name,
        String environment,
        String keyPrefix
) {
}
