package com.featureflag.audit_service.security;

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
    void missingOrBlankConfigurationFailsStartup() {
        JwtProperties missingLocation = validProperties();
        missingLocation.setPublicKeyLocation(" ");
        assertThrows(IllegalStateException.class,
                () -> configuration.jwtDecoder(missingLocation, resourceLoader));

        JwtProperties missingIssuer = validProperties();
        missingIssuer.setIssuer(null);
        assertThrows(IllegalStateException.class,
                () -> configuration.jwtDecoder(missingIssuer, resourceLoader));

        JwtProperties missingAudience = validProperties();
        missingAudience.setAudience(" ");
        assertThrows(IllegalStateException.class,
                () -> configuration.jwtDecoder(missingAudience, resourceLoader));
    }

    @Test
    void invalidPublicKeyFailsStartup() {
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
