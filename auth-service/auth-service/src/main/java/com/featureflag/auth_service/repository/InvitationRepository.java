package com.featureflag.auth_service.repository;

import com.featureflag.auth_service.entity.Invitation;
import com.featureflag.auth_service.entity.InvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    List<Invitation> findByEmailAndStatus(String email, InvitationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i
            from Invitation i
            where i.tokenHash = :tokenHash
            """)
    Optional<Invitation> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i
            from Invitation i
            where i.id = :id
            """)
    Optional<Invitation> findByIdForUpdate(
            @Param("id") Long id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i
            from Invitation i
            where i.email = :email
              and i.status = :status
            """)
    List<Invitation> findByEmailAndStatusForUpdate(
            @Param("email") String email,
            @Param("status") InvitationStatus status
    );

    List<Invitation> findAllByOrderByCreatedAtDesc();

    List<Invitation> findByStatus(InvitationStatus status);
}
