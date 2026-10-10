package com.featureflag.flag_service.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagPermissionServiceTest {

    private FlagPermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new FlagPermissionService();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "test@company.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void unauthenticatedCallIsDenied() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> permissionService.validateMutationPermission("DEV"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authenticated");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEV", "dev", "QA", "qa", "STAGING", "staging", "PROD", "prod"})
    void ownerCanMutateInAllRecognizedEnvironments(String env) {
        authenticate("OWNER");
        assertThatCode(() -> permissionService.validateMutationPermission(env)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEV", "dev", "QA", "qa", "STAGING", "staging", "PROD", "prod"})
    void adminCanMutateInAllRecognizedEnvironments(String env) {
        authenticate("ADMIN");
        assertThatCode(() -> permissionService.validateMutationPermission(env)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEV", "dev", "QA", "qa", "STAGING", "staging"})
    void developerCanMutateInDevQaAndStaging(String env) {
        authenticate("DEVELOPER");
        assertThatCode(() -> permissionService.validateMutationPermission(env)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROD", "prod"})
    void developerCannotMutateInProd(String env) {
        authenticate("DEVELOPER");
        assertThatThrownBy(() -> permissionService.validateMutationPermission(env))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("DEVELOPER role is not permitted to mutate flags in environment: PROD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCAL", "TEST", "PREPROD", "UNKNOWN", "INVALID"})
    void unknownOrUnrecognizedEnvironmentIsDeniedForDevelopers(String env) {
        authenticate("DEVELOPER");
        assertThatThrownBy(() -> permissionService.validateMutationPermission(env))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unrecognized environment: " + env);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCAL", "TEST", "PREPROD", "UNKNOWN", "INVALID"})
    void unknownOrUnrecognizedEnvironmentIsDeniedForOwnersAndAdmins(String env) {
        authenticate("OWNER");
        assertThatThrownBy(() -> permissionService.validateMutationPermission(env))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unrecognized environment: " + env);

        authenticate("ADMIN");
        assertThatThrownBy(() -> permissionService.validateMutationPermission(env))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unrecognized environment: " + env);
    }

    @Test
    void developerWithBlankEnvironmentIsDenied() {
        authenticate("DEVELOPER");
        assertThatThrownBy(() -> permissionService.validateMutationPermission("   "))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("environment is missing or invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEV", "QA", "STAGING", "PROD"})
    void viewerCannotMutateInAnyEnvironment(String env) {
        authenticate("VIEWER");
        assertThatThrownBy(() -> permissionService.validateMutationPermission(env))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Role is not permitted to mutate flags");
    }
}
