package com.featureflag.auth_service.repository;

import com.featureflag.auth_service.entity.Invitation;
import com.featureflag.auth_service.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    List<Invitation> findByEmailAndStatus(String email, InvitationStatus status);

    List<Invitation> findAllByOrderByCreatedAtDesc();

    List<Invitation> findByStatus(InvitationStatus status);
}
