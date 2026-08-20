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

import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
