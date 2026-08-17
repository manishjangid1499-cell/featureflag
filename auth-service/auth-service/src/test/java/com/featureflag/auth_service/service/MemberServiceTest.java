package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.MemberRequest;
import com.featureflag.auth_service.dto.MemberResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    private User ownerUser;
    private User adminUser;
    private User devUser;

    @BeforeEach
    void setUp() {
        ownerUser = User.builder().id(1L).email("owner@company.com").role(Role.OWNER).build();
        adminUser = User.builder().id(2L).email("admin@company.com").role(Role.ADMIN).build();
        devUser = User.builder().id(3L).email("dev@company.com").role(Role.DEVELOPER).build();
    }

    @Test
    @DisplayName("Create Member - OWNER can create ADMIN")
    void testCreateMember_OwnerCreatesAdmin() {
        MemberRequest request = new MemberRequest("Admin User", "admin2@company.com", "pass123", Role.ADMIN);

        when(userRepository.findByEmail("admin2@company.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(20L);
            return u;
        });

        MemberResponse response = memberService.createMember(request, ownerUser);

        assertNotNull(response);
        assertEquals(Role.ADMIN, response.getRole());
    }

    @Test
    @DisplayName("Create Member - ADMIN cannot create ADMIN (Throws RuntimeException)")
    void testCreateMember_AdminCannotCreateAdmin() {
        MemberRequest request = new MemberRequest("Admin 2", "admin2@company.com", "pass123", Role.ADMIN);

        when(userRepository.findByEmail("admin2@company.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> memberService.createMember(request, adminUser));
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
