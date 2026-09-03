package com.featureflag.audit_service.controller;

import com.featureflag.audit_service.dto.AuditLogResponse;
import com.featureflag.audit_service.dto.PageResponse;
import com.featureflag.audit_service.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "APIs for querying feature flag audit logs")
public class AuditController {

    private final AuditService auditService;

    @Operation(summary = "Get all audit logs", description = "Returns all audit log records ordered by latest")
    @GetMapping
    public ResponseEntity<PageResponse<AuditLogResponse>> getAllAuditLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(PageResponse.from(
                auditService.getAllAuditLogs(PageRequest.of(page, size)),
                AuditLogResponse::from
        ));
    }

    @Operation(summary = "Get audit logs by flag key", description = "Returns audit log history for a specific flag")
    @GetMapping("/{flagKey}")
    public ResponseEntity<PageResponse<AuditLogResponse>> getAuditLogsByFlagKey(
            @PathVariable String flagKey,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(PageResponse.from(
                auditService.getAuditLogsByFlagKey(
                        flagKey,
                        PageRequest.of(page, size)
                ),
                AuditLogResponse::from
        ));
    }

    @Operation(summary = "Get audit log by ID", description = "Returns a single audit log entry")
    @GetMapping("/id/{id}")
    public ResponseEntity<AuditLogResponse> getAuditLogById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                AuditLogResponse.from(auditService.getAuditLogById(id))
        );
    }
}
