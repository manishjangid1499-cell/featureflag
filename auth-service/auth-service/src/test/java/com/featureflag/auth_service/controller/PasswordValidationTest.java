package com.featureflag.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.auth_service.exception.GlobalExceptionHandler;
import com.featureflag.auth_service.exception.ApiProblemDetails;
import com.featureflag.auth_service.service.AuthService;
import com.featureflag.auth_service.service.InvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordValidationTest {
    private final AuthService auth = mock(AuthService.class);
    private final InvitationService invitations = mock(InvitationService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth),
                        new InvitationController(invitations))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemDetails(mapper))).build();
    }

    static Stream<String> supportedPasswords() {
        return Stream.of("normal-password", "a".repeat(72), "\u00e9".repeat(36),
                "\ud83d\udd12".repeat(18));
    }

    static Stream<String> oversizedPasswords() {
        return Stream.of("a".repeat(73), "\u00e9".repeat(37),
                "\ud83d\udd12".repeat(18) + "a");
    }

    @ParameterizedTest
    @MethodSource("supportedPasswords")
    void supportedUtf8PasswordsReachBothEntryPoints(String password) throws Exception {
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        assertThat(encoder.matches(password, encoder.encode(password))).isTrue();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "member@example.test", "password", password))))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(invitationJson(password, password)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("oversizedPasswords")
    void oversizedUtf8PasswordsAreClientErrorsAndNeverEchoed(String password) throws Exception {
        String loginError = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "member@example.test", "password", password))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String invitationError = mvc.perform(post("/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON).content(invitationJson(password, password)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(loginError).doesNotContain(password);
        assertThat(invitationError).doesNotContain(password);
        verifyNoInteractions(auth, invitations);
    }

    @ParameterizedTest
    @MethodSource("oversizedPasswords")
    void confirmationUsesTheSameByteLimit(String password) throws Exception {
        mvc.perform(post("/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(invitationJson("normal-password", password)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(invitations);
    }

    private String invitationJson(String password, String confirmation) throws Exception {
        return mapper.writeValueAsString(Map.of("token", "test-invitation",
                "password", password, "confirmPassword", confirmation));
    }
}
