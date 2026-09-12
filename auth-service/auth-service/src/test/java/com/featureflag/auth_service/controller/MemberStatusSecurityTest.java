package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.config.SecurityConfig;
import com.featureflag.auth_service.dto.MemberResponse;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.security.AuthRecipientsServiceKeyFilter;
import com.featureflag.auth_service.security.CustomUserDetailsService;
import com.featureflag.auth_service.security.JwtAuthenticationFilter;
import com.featureflag.auth_service.security.JwtService;
import com.featureflag.auth_service.service.InvitationService;
import com.featureflag.auth_service.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import({
        SecurityConfig.class,
        AuthRecipientsServiceKeyFilter.class,
        JwtAuthenticationFilter.class
})
@TestPropertySource(properties =
        "AUTH_RECIPIENTS_SERVICE_KEY=test-recipients-key")
class MemberStatusSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private InvitationService invitationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void ownerCanDisableMember() throws Exception {
        User owner = authenticate("owner-token", Role.OWNER);
        when(memberService.updateEnabled(7L, false, owner))
                .thenReturn(new MemberResponse(
                        7L,
                        "Viewer",
                        "viewer@company.com",
                        Role.VIEWER,
                        false
                ));

        mockMvc.perform(patch("/members/7/status")
                        .header("Authorization", "Bearer owner-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(memberService).updateEnabled(7L, false, owner);
    }

    @Test
    void viewerCannotToggleMemberStatus() throws Exception {
        authenticate("viewer-token", Role.VIEWER);

        mockMvc.perform(patch("/members/7/status")
                        .header("Authorization", "Bearer viewer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingEnabledValueIsRejectedBeforeService() throws Exception {
        authenticate("admin-token", Role.ADMIN);

        mockMvc.perform(patch("/members/7/status")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private User authenticate(String token, Role role) {
        User user = User.builder()
                .id(1L)
                .email("actor@company.com")
                .password("encoded")
                .role(role)
                .build();
        when(jwtService.extractEmail(token))
                .thenReturn(user.getEmail());
        when(customUserDetailsService.loadUserByUsername(user.getEmail()))
                .thenReturn(user);
        when(jwtService.isTokenValid(token, user.getEmail()))
                .thenReturn(true);
        return user;
    }
}
