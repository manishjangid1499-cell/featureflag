package com.featureflag.audit_service.service;

import com.featureflag.audit_service.entity.AuditLog;
import com.featureflag.audit_service.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public List<AuditLog> getAllAuditLogs() {
        return auditLogRepository.findAllByOrderByIdDesc();
    }

    public List<AuditLog> getAuditLogsByFlagKey(String flagKey) {
        return auditLogRepository.findByFlagKeyOrderByTimestampDesc(flagKey);
    }

    public AuditLog getAuditLogById(Long id) {
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit log not found with id: " + id));
    }
}
