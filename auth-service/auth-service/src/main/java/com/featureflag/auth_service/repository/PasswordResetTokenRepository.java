package com.featureflag.auth_service.repository;

import com.featureflag.auth_service.entity.PasswordResetStatus;
import com.featureflag.auth_service.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM PasswordResetToken t
            WHERE t.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM PasswordResetToken t
            WHERE t.userId = :userId
              AND t.status = :status
            """)
    List<PasswordResetToken> findByUserIdAndStatusForUpdate(
            @Param("userId") Long userId,
            @Param("status") PasswordResetStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM PasswordResetToken t
            WHERE t.email = :email
              AND t.status = :status
            """)
    List<PasswordResetToken> findByEmailAndStatusForUpdate(
            @Param("email") String email,
            @Param("status") PasswordResetStatus status
    );
}
