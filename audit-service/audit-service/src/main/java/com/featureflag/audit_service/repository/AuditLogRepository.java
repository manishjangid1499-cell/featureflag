package com.featureflag.audit_service.repository;

import com.featureflag.audit_service.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByFlagKeyOrderByOccurredAtDescIdDesc(
            String flagKey
    );
    List<AuditLog> findAllByOrderByIdDesc();

    Page<AuditLog> findByFlagKeyOrderByOccurredAtDescIdDesc(
            String flagKey,
            Pageable pageable
    );

    Page<AuditLog> findAllByOrderByIdDesc(Pageable pageable);
}
