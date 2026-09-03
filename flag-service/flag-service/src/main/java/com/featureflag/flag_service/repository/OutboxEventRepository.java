package com.featureflag.flag_service.repository;

import com.featureflag.flag_service.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent>
    findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            String status,
            Instant nextAttemptAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select event from OutboxEvent event "
                    + "where event.id = :id"
    )
    Optional<OutboxEvent> findByIdForUpdate(
            @Param("id") String id
    );

    @Query("""
            select event.id
            from OutboxEvent event
            where event.status = :status
              and event.publishedAt < :cutoff
            order by event.publishedAt, event.id
            """)
    List<String> findPublishedIdsBefore(
            @Param("status") String status,
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from OutboxEvent event
            where event.status = :status
              and event.publishedAt < :cutoff
              and event.id in :ids
            """)
    int deletePublishedByIdIn(
            @Param("status") String status,
            @Param("cutoff") Instant cutoff,
            @Param("ids") List<String> ids
    );
}
