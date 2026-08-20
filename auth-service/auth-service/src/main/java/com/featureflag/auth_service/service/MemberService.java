package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.MemberResponse;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final UserRepository userRepository;
    /**
     * Get all members.
     */
    public List<MemberResponse> getAllMembers() {

        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get member by ID.
     */
    public MemberResponse getMember(Long id) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Member not found with id: "
                                                + id
                                )
                        );

        return toResponse(user);
    }

    /**
     * Change a member's role.
     */
    public MemberResponse updateRole(
            Long id,
            Role newRole,
            User currentUser
    ) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Member not found with id: "
                                                + id
                                )
                        );

        /*
         * Nobody can change their own role
         * through this endpoint.
         */
        if (user.getId().equals(currentUser.getId())) {

            throw new RuntimeException(
                    "You cannot change your own role"
            );
        }

        validateRoleCreationPermission(
                currentUser.getRole(),
                newRole
        );

        /*
         * ADMIN cannot modify OWNER.
         */
        if (user.getRole() == Role.OWNER
                && currentUser.getRole() != Role.OWNER) {

            throw new RuntimeException(
                    "Only OWNER can modify OWNER"
            );
        }

        user.setRole(newRole);

        User updatedUser =
                userRepository.save(user);

        return toResponse(updatedUser);
    }

    /**
     * Delete a member.
     */
    public void deleteMember(
            Long id,
            User currentUser
    ) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Member not found with id: "
                                                + id
                                )
                        );

        /*
         * Nobody can delete themselves.
         */
        if (user.getId().equals(currentUser.getId())) {

            throw new RuntimeException(
                    "You cannot delete yourself"
            );
        }

        /*
         * OWNER cannot be deleted by ADMIN.
         */
        if (user.getRole() == Role.OWNER
                && currentUser.getRole() != Role.OWNER) {

            throw new RuntimeException(
                    "Only OWNER can delete OWNER"
            );
        }

        /*
         * ADMIN can only delete
         * DEVELOPER and VIEWER.
         */
        if (currentUser.getRole() == Role.ADMIN
                && user.getRole() != Role.DEVELOPER
                && user.getRole() != Role.VIEWER) {

            throw new RuntimeException(
                    "ADMIN can only delete DEVELOPER or VIEWER"
            );
        }

        userRepository.delete(user);
    }

    /**
     * Validate whether the current user can
     * create/assign the requested role.
     */
    private void validateRoleCreationPermission(
            Role currentRole,
            Role requestedRole
    ) {

        if (currentRole == Role.OWNER) {

            if (requestedRole == Role.OWNER) {

                throw new RuntimeException(
                        "OWNER cannot create another OWNER"
                );
            }

            return;
        }

        if (currentRole == Role.ADMIN) {

            if (requestedRole == Role.ADMIN
                    || requestedRole == Role.OWNER) {

                throw new RuntimeException(
                        "ADMIN cannot create or assign ADMIN/OWNER"
                );
            }

            return;
        }

        throw new RuntimeException(
                "You do not have permission to manage members"
        );
    }

    private MemberResponse toResponse(User user) {

        return new MemberResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}