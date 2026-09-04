package com.featureflag.auth_service.service;

import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MemberService memberService;

    private User ownerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        ownerUser = User.builder().id(1L).email("owner@company.com").role(Role.OWNER).build();
        adminUser = User.builder().id(2L).email("admin@company.com").role(Role.ADMIN).build();
    }

    @Test
    @DisplayName("Update Role - Cannot change own role")
    void testUpdateRole_CannotChangeOwnRole() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(ownerUser));

        assertThrows(RuntimeException.class, () -> memberService.updateRole(1L, Role.ADMIN, ownerUser));
    }

    @Test
    @DisplayName("Delete Member - ADMIN cannot delete OWNER")
    void testDeleteMember_AdminCannotDeleteOwner() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(ownerUser));

        assertThrows(RuntimeException.class, () -> memberService.deleteMember(1L, adminUser));
    }

    @Test
    @DisplayName("OWNER can disable and re-enable another member")
    void updateEnabled_OwnerCanToggleAnotherMember() {
        User developer = User.builder()
                .id(3L)
                .email("developer@company.com")
                .role(Role.DEVELOPER)
                .build();
        when(userRepository.findById(3L))
                .thenReturn(Optional.of(developer));
        when(userRepository.save(developer)).thenReturn(developer);

        var disabled = memberService.updateEnabled(3L, false, ownerUser);
        assertFalse(disabled.isEnabled());

        var enabled = memberService.updateEnabled(3L, true, ownerUser);
        assertTrue(enabled.isEnabled());
        assertEquals("developer@company.com", enabled.getEmail());
        verify(userRepository, org.mockito.Mockito.times(2)).save(developer);
    }

    @Test
    @DisplayName("Member response exposes status but never password")
    void getMember_MapsEnabledStatus() {
        User disabled = User.builder()
                .id(3L)
                .email("viewer@company.com")
                .password("secret-hash")
                .role(Role.VIEWER)
                .enabled(false)
                .build();
        when(userRepository.findById(3L))
                .thenReturn(Optional.of(disabled));

        var response = memberService.getMember(3L);

        assertFalse(response.isEnabled());
        assertEquals("viewer@company.com", response.getEmail());
    }

    @Test
    @DisplayName("Member cannot disable their own account")
    void updateEnabled_CannotDisableSelf() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(ownerUser));

        assertThrows(
                RuntimeException.class,
                () -> memberService.updateEnabled(
                        1L,
                        false,
                        ownerUser
                )
        );
    }

    @Test
    @DisplayName("ADMIN cannot disable another ADMIN")
    void updateEnabled_AdminCannotDisableAdmin() {
        User secondAdmin = User.builder()
                .id(4L)
                .email("second-admin@company.com")
                .role(Role.ADMIN)
                .build();
        when(userRepository.findById(4L))
                .thenReturn(Optional.of(secondAdmin));

        assertThrows(
                RuntimeException.class,
                () -> memberService.updateEnabled(
                        4L,
                        false,
                        adminUser
                )
        );
    }

    @Test
    @DisplayName("Non-manager cannot toggle member status")
    void updateEnabled_ViewerCannotToggle() {
        User viewer = User.builder()
                .id(5L)
                .role(Role.VIEWER)
                .build();

        assertThrows(
                RuntimeException.class,
                () -> memberService.updateEnabled(
                        3L,
                        false,
                        viewer
                )
        );
    }
}
