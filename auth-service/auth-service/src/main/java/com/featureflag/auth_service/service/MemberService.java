package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.MemberResponse;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.exception.ForbiddenException;
import com.featureflag.auth_service.exception.InvalidOperationException;
import com.featureflag.auth_service.exception.ResourceNotFoundException;
import com.featureflag.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    public Page<MemberResponse> getAllMembers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Get member by ID.
     */
    public MemberResponse getMember(Long id) {

        User user =
                userRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
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
                                new ResourceNotFoundException(
                                        "Member not found with id: "
                                                + id
                                )
                        );

        /*
         * Nobody can change their own role
         * through this endpoint.
         */
        if (user.getId().equals(currentUser.getId())) {

            throw new InvalidOperationException(
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

            throw new ForbiddenException(
                    "Only OWNER can modify OWNER"
            );
        }

        user.setRole(newRole);

        User updatedUser =
                userRepository.save(user);

        return toResponse(updatedUser);
    }

    /**
     * Enable or disable a member account without deleting it.
     */
    public MemberResponse updateEnabled(
            Long id,
            boolean enabled,
            User currentUser
    ) {
        requireMemberManager(currentUser);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Member not found with id: " + id
                ));

        if (!enabled && user.getId().equals(currentUser.getId())) {
            throw new InvalidOperationException(
                    "You cannot disable your own account"
            );
        }

        if (currentUser.getRole() == Role.ADMIN
                && user.getRole() != Role.DEVELOPER
                && user.getRole() != Role.VIEWER) {
            throw new ForbiddenException(
                    "ADMIN can only enable or disable DEVELOPER or VIEWER"
            );
        }

        user.setEnabled(enabled);
        return toResponse(userRepository.save(user));
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
                                new ResourceNotFoundException(
                                        "Member not found with id: "
                                                + id
                                )
                        );

        /*
         * Nobody can delete themselves.
         */
        if (user.getId().equals(currentUser.getId())) {

            throw new InvalidOperationException(
                    "You cannot delete yourself"
            );
        }

        /*
         * OWNER cannot be deleted by ADMIN.
         */
        if (user.getRole() == Role.OWNER
                && currentUser.getRole() != Role.OWNER) {

            throw new ForbiddenException(
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

            throw new ForbiddenException(
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

                throw new InvalidOperationException(
                        "OWNER cannot create another OWNER"
                );
            }

            return;
        }

        if (currentRole == Role.ADMIN) {

            if (requestedRole == Role.ADMIN
                    || requestedRole == Role.OWNER) {

                throw new ForbiddenException(
                        "ADMIN cannot create or assign ADMIN/OWNER"
                );
            }

            return;
        }

        throw new ForbiddenException(
                "You do not have permission to manage members"
        );
    }

    private void requireMemberManager(User currentUser) {
        if (currentUser == null
                || (currentUser.getRole() != Role.OWNER
                && currentUser.getRole() != Role.ADMIN)) {
            throw new ForbiddenException(
                    "You do not have permission to manage members"
            );
        }
    }

    private MemberResponse toResponse(User user) {

        return new MemberResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled()
        );
    }
}
