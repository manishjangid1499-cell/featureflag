package com.featureflag.flag_service.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtSecurityConfigTest {

    private final JwtSecurityConfig configuration = new JwtSecurityConfig();
    private final ResourceLoader resourceLoader = mock(ResourceLoader.class);

    @Test
    void missingPublicKeyLocationFailsStartup() {
        JwtProperties properties = validProperties();
        properties.setPublicKeyLocation(" ");

        assertThrows(IllegalStateException.class,
                () -> configuration.jwtDecoder(properties, resourceLoader));
    }

    @Test
    void blankIssuerFailsStartup() {
        JwtProperties properties = validProperties();
        properties.setIssuer(" ");

        assertThrows(IllegalStateException.class,
                () -> configuration.jwtDecoder(properties, resourceLoader));
    }

    @Test
    void blankAudienceFailsStartup() {
        JwtProperties properties = validProperties();
        properties.setAudience(null);

        assertThrows(IllegalStateException.class,
                () -> configuration.jwtDecoder(properties, resourceLoader));
    }

    @Test
    void unreadableOrInvalidPublicKeyFailsStartup() {
        JwtProperties properties = validProperties();
        when(resourceLoader.getResource("file:test-public.pem"))
                .thenReturn(new ByteArrayResource("not a public key".getBytes()));

        assertThrows(IllegalStateException.class,
                () -> configuration.jwtDecoder(properties, resourceLoader));
    }

    private static JwtProperties validProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setPublicKeyLocation("file:test-public.pem");
        properties.setIssuer("feature-flag-auth");
        properties.setAudience("feature-flag-api");
        return properties;
    }
}
