package com.featureflag.notification_service.repository;

import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByStatus(String status);

    List<Notification> findAllByOrderByCreatedAtDesc();

    Page<Notification> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<Notification> findByRecipient(String recipient);

    List<Notification> findByRecipientOrderByCreatedAtDesc(String recipient);

    List<Notification> findByRecipientIgnoreCaseOrderByCreatedAtDesc(String recipient);

    Page<Notification> findByRecipientIgnoreCaseOrderByCreatedAtDescIdDesc(
            String recipient,
            Pageable pageable
    );

    List<Notification> findByRecipientOrCreatorEmailOrderByCreatedAtDesc(String recipient, String creatorEmail);

    List<Notification> findByRecipientIgnoreCaseOrCreatorEmailIgnoreCaseOrderByCreatedAtDesc(
            String recipient,
            String creatorEmail
    );

    Page<Notification> findByRecipientIgnoreCaseOrCreatorEmailIgnoreCaseOrderByCreatedAtDescIdDesc(
            String recipient,
            String creatorEmail,
            Pageable pageable
    );

    List<Notification> findByStatus(String status);

    Page<Notification> findByStatusOrderByCreatedAtDescIdDesc(
            String status,
            Pageable pageable
    );

    @Query(value = """
            SELECT n.*
            FROM notifications n
            WHERE n.delivery_mode = 'DURABLE'
              AND n.status IN ('PENDING', 'RETRY')
              AND n.next_attempt_at <= :now
            ORDER BY n.next_attempt_at, n.id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Notification> findNextDueForUpdateSkipLocked(
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n
            from Notification n
            where n.deliveryMode = :deliveryMode
              and n.status = :status
              and n.leaseUntil <= :now
            order by n.leaseUntil, n.id
            """)
    List<Notification> findExpiredLeasesForUpdate(
            @Param("deliveryMode") DeliveryMode deliveryMode,
            @Param("status") String status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n
            from Notification n
            where n.id = :id
            """)
    Optional<Notification> findByIdForUpdate(
            @Param("id") Long id
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = 'SENT',
                n.sentAt = :sentAt,
                n.nextAttemptAt = null,
                n.leaseUntil = null,
                n.claimToken = null
            where n.id = :id
              and n.deliveryMode = :deliveryMode
              and n.status = 'PROCESSING'
              and n.claimToken = :claimToken
            """)
    int markSentIfClaimMatches(
            @Param("id") Long id,
            @Param("deliveryMode") DeliveryMode deliveryMode,
            @Param("claimToken") String claimToken,
            @Param("sentAt") Instant sentAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = 'RETRY',
                n.sentAt = null,
                n.nextAttemptAt = :nextAttemptAt,
                n.leaseUntil = null,
                n.claimToken = null,
                n.lastErrorType = :lastErrorType
            where n.id = :id
              and n.deliveryMode = :deliveryMode
              and n.status = 'PROCESSING'
              and n.claimToken = :claimToken
            """)
    int markRetryIfClaimMatches(
            @Param("id") Long id,
            @Param("deliveryMode") DeliveryMode deliveryMode,
            @Param("claimToken") String claimToken,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("lastErrorType") String lastErrorType
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = 'DEAD',
                n.sentAt = null,
                n.nextAttemptAt = null,
                n.leaseUntil = null,
                n.claimToken = null,
                n.lastErrorType = :lastErrorType
            where n.id = :id
              and n.deliveryMode = :deliveryMode
              and n.status = 'PROCESSING'
              and n.claimToken = :claimToken
            """)
    int markDeadIfClaimMatches(
            @Param("id") Long id,
            @Param("deliveryMode") DeliveryMode deliveryMode,
            @Param("claimToken") String claimToken,
            @Param("lastErrorType") String lastErrorType
    );
}
