package com.featureflag.auth_service.config;

import com.featureflag.auth_service.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OwnerBootstrapPasswordTest {
    @ParameterizedTest
    @ValueSource(strings = {"a", "\u00e9", "\ud83d\udd12"})
    void oversizedBootstrapPasswordFailsWithSafeConfigurationError(String character) {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        OwnerBootstrapProperties properties = new OwnerBootstrapProperties();
        properties.setEnabled(true);
        properties.setName("Fixture Owner");
        properties.setEmail("owner@example.test");
        properties.setPassword(character.repeat(73));

        assertThatThrownBy(() -> new OwnerBootstrapService(users, encoder, properties)
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bootstrap OWNER password must not exceed 72 UTF-8 bytes.");
        verifyNoInteractions(encoder);
    }
}
