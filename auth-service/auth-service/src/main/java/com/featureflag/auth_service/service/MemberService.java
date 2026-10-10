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
import org.springframework.transaction.annotation.Transactional;

import com.featureflag.auth_service.repository.SystemInvariantRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final UserRepository userRepository;
    private final SystemInvariantRepository systemInvariantRepository;
    private final PasswordEncoder passwordEncoder;
    public Page<MemberResponse> getAllMembers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

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

    @Transactional
    public MemberResponse updateRole(
            Long id,
            Role newRole,
            User currentUser
    ) {
        /*
         * Nobody can change their own role
         * through this endpoint.
         */
        if (id.equals(currentUser.getId())) {
            throw new InvalidOperationException(
                    "You cannot change your own role"
            );
        }

        User user =
                userRepository.findByIdForUpdate(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Member not found with id: "
                                                + id
                                )
                        );

        requireManageableTarget(currentUser, user);
        validateRoleCreationPermission(
                currentUser.getRole(),
                newRole
        );

        user.setRole(newRole);

        User updatedUser =
                userRepository.save(user);

        return toResponse(updatedUser);
    }

    @Transactional
    public MemberResponse updateEnabled(
            Long id,
            boolean enabled,
            User currentUser
    ) {
        requireMemberManager(currentUser);

        if (!enabled && id.equals(currentUser.getId())) {
            throw new InvalidOperationException(
                    "You cannot disable your own account"
            );
        }

        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Member not found with id: " + id
                ));

        requireManageableTarget(currentUser, user);

        user.setEnabled(enabled);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteMember(
            Long id,
            User currentUser
    ) {
        /*
         * Nobody can delete themselves.
         */
        if (id.equals(currentUser.getId())) {
            throw new InvalidOperationException(
                    "You cannot delete yourself"
            );
        }

        User user =
                userRepository.findByIdForUpdate(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Member not found with id: "
                                                + id
                                )
                        );

        requireManageableTarget(currentUser, user);

        userRepository.delete(user);
    }

    @Transactional
    public MemberResponse transferOwnership(
            com.featureflag.auth_service.dto.TransferOwnershipRequest request,
            User currentUser
    ) {
        if (currentUser == null) {
            throw new ForbiddenException("Authentication required");
        }

        // Lock cluster singleton invariant row first to serialize any bootstrap or transfer cluster-wide
        com.featureflag.auth_service.entity.SystemInvariant invariant = systemInvariantRepository
                .findByNameForUpdate(com.featureflag.auth_service.entity.SystemInvariant.OWNER_INSTALLATION)
                .orElseGet(() -> systemInvariantRepository.save(
                        com.featureflag.auth_service.entity.SystemInvariant.builder()
                                .name(com.featureflag.auth_service.entity.SystemInvariant.OWNER_INSTALLATION)
                                .updatedAt(java.time.Instant.now())
                                .build()
                ));

        Long callerId = currentUser.getId();
        Long targetId = request.newOwnerId();

        if (callerId.equals(targetId)) {
            throw new InvalidOperationException("Cannot transfer ownership to yourself");
        }

        // Lock accounts in canonical ascending order by ID to prevent deadlocks
        Long firstId = Math.min(callerId, targetId);
        Long secondId = Math.max(callerId, targetId);

        User firstUser = userRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + firstId));
        User secondUser = userRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + secondId));

        User caller = callerId.equals(firstId) ? firstUser : secondUser;
        User target = targetId.equals(firstId) ? firstUser : secondUser;

        // Recheck caller identity, role, enabled status, and credentialVersion
        if (caller.getRole() != Role.OWNER) {
            throw new ForbiddenException("Only the current OWNER can transfer ownership");
        }
        if (!caller.isEnabled()) {
            throw new ForbiddenException("Caller account is disabled");
        }
        if (currentUser.getCredentialVersion() != null
                && !currentUser.getCredentialVersion().equals(caller.getCredentialVersion())) {
            throw new ForbiddenException("Session credentials are stale");
        }

        // Reauthenticate caller current password
        if (request.currentPassword() == null || request.currentPassword().isBlank()) {
            throw new InvalidOperationException("Current password is required");
        }
        if (!passwordEncoder.matches(request.currentPassword(), caller.getPassword())) {
            throw new InvalidOperationException("Current password does not match");
        }

        // Target must be an active ADMIN
        if (target.getRole() != Role.ADMIN) {
            throw new InvalidOperationException("Ownership can only be transferred to an active ADMIN");
        }
        if (!target.isEnabled()) {
            throw new InvalidOperationException("Cannot transfer ownership to a disabled account");
        }

        // Atomically transfer ownership
        caller.setRole(Role.ADMIN);
        caller.setCredentialVersion((caller.getCredentialVersion() != null ? caller.getCredentialVersion() : 1L) + 1L);

        target.setRole(Role.OWNER);
        target.setCredentialVersion((target.getCredentialVersion() != null ? target.getCredentialVersion() : 1L) + 1L);

        userRepository.save(caller);
        User savedTarget = userRepository.save(target);

        invariant.setOwnerUserId(savedTarget.getId());
        invariant.setUpdatedAt(java.time.Instant.now());
        systemInvariantRepository.save(invariant);

        return toResponse(savedTarget);
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

    private void requireManageableTarget(User currentUser, User target) {
        requireMemberManager(currentUser);
        if (target.getRole() == Role.OWNER) {
            throw new InvalidOperationException("OWNER account cannot be modified or deleted through member management");
        }
        if (currentUser.getRole() == Role.ADMIN
                && target.getRole() != Role.DEVELOPER
                && target.getRole() != Role.VIEWER) {
            throw new ForbiddenException("ADMIN can only manage DEVELOPER or VIEWER");
        }
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
