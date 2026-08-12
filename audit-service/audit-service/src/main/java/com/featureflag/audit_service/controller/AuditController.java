package com.featureflag.audit_service.controller;

import com.featureflag.audit_service.entity.AuditLog;
import com.featureflag.audit_service.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "APIs for querying feature flag audit logs")
public class AuditController {

    private final AuditService auditService;

    @Operation(summary = "Get all audit logs", description = "Returns all audit log records ordered by latest")
    @GetMapping
    public ResponseEntity<List<AuditLog>> getAllAuditLogs() {
        return ResponseEntity.ok(auditService.getAllAuditLogs());
    }

    @Operation(summary = "Get audit logs by flag key", description = "Returns audit log history for a specific flag")
    @GetMapping("/{flagKey}")
    public ResponseEntity<List<AuditLog>> getAuditLogsByFlagKey(@PathVariable String flagKey) {
        return ResponseEntity.ok(auditService.getAuditLogsByFlagKey(flagKey));
    }

    @Operation(summary = "Get audit log by ID", description = "Returns a single audit log entry")
    @GetMapping("/id/{id}")
    public ResponseEntity<AuditLog> getAuditLogById(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.getAuditLogById(id));
    }
}
