package com.featureflag.auth_service.security;

import com.featureflag.auth_service.config.JwtProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@TestConfiguration(proxyBeanMethods = false)
public class EphemeralJwtTestConfiguration {

    @Bean
    JwtService jwtService(JwtProperties properties) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        return new JwtService(
                (RSAPrivateKey) keyPair.getPrivate(),
                (RSAPublicKey) keyPair.getPublic(),
                properties.getIssuer(),
                properties.getAudience(),
                properties.getAccessTokenTtl(),
                properties.getKeyId()
        );
    }
}
