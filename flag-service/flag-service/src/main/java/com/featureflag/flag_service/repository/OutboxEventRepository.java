package com.featureflag.flag_service.repository;

import com.featureflag.flag_service.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent>
    findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            String status,
            LocalDateTime nextAttemptAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select event from OutboxEvent event "
                    + "where event.id = :id"
    )
    Optional<OutboxEvent> findByIdForUpdate(
            @Param("id") String id
    );
}
