package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.config.SecurityConfig;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, AuthRecipientsServiceKeyFilter.class,
        JwtAuthenticationFilter.class, MemberService.class})
@TestPropertySource(properties = "AUTH_RECIPIENTS_SERVICE_KEY=test-recipients-key")
class MemberAdministrationSecurityTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private UserRepository users;
    @MockitoBean private InvitationService invitations;
    @MockitoBean private JwtService jwt;
    @MockitoBean private CustomUserDetailsService userDetails;

    @Test
    void adminCannotDemotePeerAdmin() throws Exception {
        authenticate(Role.ADMIN);
        target(Role.ADMIN);
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(patch("/members/2/role").param("role", "VIEWER")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verify(users, never()).save(any());
    }

    @Test
    void adminCannotDisablePeerAdmin() throws Exception {
        authenticate(Role.ADMIN);
        target(Role.ADMIN);
        mvc.perform(patch("/members/2/status")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        verify(users, never()).save(any());
    }

    @Test
    void adminCannotDeletePeerAdmin() throws Exception {
        authenticate(Role.ADMIN);
        target(Role.ADMIN);
        mvc.perform(delete("/members/2").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
        verify(users, never()).delete(any());
    }

    @Test
    void ownerCanDemoteAdmin() throws Exception {
        authenticate(Role.OWNER);
        User target = target(Role.ADMIN);
        when(users.save(target)).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(patch("/members/2/role").param("role", "DEVELOPER")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    void adminCanChangeLowerRoleAndDisableIt() throws Exception {
        authenticate(Role.ADMIN);
        User target = target(Role.DEVELOPER);
        when(users.save(target)).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(patch("/members/2/role").param("role", "VIEWER")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        mvc.perform(patch("/members/2/status")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void adminCanDeleteLowerRole() throws Exception {
        authenticate(Role.ADMIN);
        User target = target(Role.VIEWER);
        mvc.perform(delete("/members/2").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        verify(users).delete(target);
    }

    private User target(Role role) {
        User target = User.builder().id(2L).email("target@example.test").role(role).build();
        when(users.findById(2L)).thenReturn(Optional.of(target));
        return target;
    }

    private void authenticate(Role role) {
        User actor = User.builder().id(1L).email("actor@example.test").role(role).build();
        when(jwt.extractEmail("test-token")).thenReturn(actor.getEmail());
        when(userDetails.loadUserByUsername(actor.getEmail())).thenReturn(actor);
        when(jwt.isTokenValid("test-token", actor.getEmail())).thenReturn(true);
    }
}
