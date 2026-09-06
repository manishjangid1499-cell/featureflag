package com.featureflag.audit_service.service;

import com.featureflag.audit_service.entity.AuditLog;
import com.featureflag.audit_service.exception.ResourceNotFoundException;
import com.featureflag.audit_service.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public List<AuditLog> getAllAuditLogs() {
        return auditLogRepository.findAllByOrderByIdDesc();
    }

    public Page<AuditLog> getAllAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByIdDesc(pageable);
    }

    public List<AuditLog> getAuditLogsByFlagKey(String flagKey) {
        return auditLogRepository
                .findByFlagKeyOrderByOccurredAtDescIdDesc(
                        flagKey
                );
    }

    public Page<AuditLog> getAuditLogsByFlagKey(
            String flagKey,
            Pageable pageable
    ) {
        return auditLogRepository
                .findByFlagKeyOrderByOccurredAtDescIdDesc(
                        flagKey,
                        pageable
                );
    }

    public AuditLog getAuditLogById(Long id) {
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Audit log not found with id: " + id
                ));
    }
}
