package com.featureflag.flag_service.service;

import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.dto.FlagToggleRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.event.FlagAuditSnapshot;
import com.featureflag.flag_service.security.FlagPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.Objects;
import com.featureflag.flag_service.exception.InvalidOperationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@Service
@RequiredArgsConstructor
@Transactional
public class FlagMutationAuditService {

    private final FlagService flagService;
    private final FlagAuditContext auditContext;
    private final FlagPermissionService permissionService;

    public FeatureFlag createFlag(
            FlagRequest request,
            String actor
    ) {
        String canonicalEnv = FlagService.normalizeEnvironment(request.getEnvironment());
        permissionService.validateMutationPermission(canonicalEnv);
        request.setFlagKey(request.getFlagKey().toLowerCase(Locale.ROOT));
        return auditContext.within(
                new FlagAuditContext.AuditDetails(
                        actor,
                        null
                ),
                () -> flagService.createFlag(request)
        );
    }

    public FeatureFlag updateFlag(
            Long id,
            FlagRequest request,
            String actor
    ) {
        FeatureFlag existing = flagService.getById(id);
        permissionService.validateMutationPermission(existing.getEnvironment());
        if (request.getExpectedVersion() == null) {
            throw new InvalidOperationException("expectedVersion is required when updating a flag");
        }
        if (!Objects.equals(existing.getVersion(), request.getExpectedVersion())) {
            throw new ObjectOptimisticLockingFailureException(FeatureFlag.class, id);
        }
        FlagService.validateIdentityImmutability(existing, request);

        // Preserve stored canonical identity so an edit cannot alter stored casing or identity.
        request.setFlagKey(existing.getFlagKey());
        request.setEnvironment(existing.getEnvironment());
        FlagAuditSnapshot before = FlagAuditSnapshot.from(
                existing
        );

        return auditContext.within(
                new FlagAuditContext.AuditDetails(
                        actor,
                        before
                ),
                () -> flagService.updateFlag(id, request)
        );
    }

    public String deleteFlag(Long id, String actor) {
        FeatureFlag existing = flagService.getById(id);
        permissionService.validateMutationPermission(existing.getEnvironment());
        FlagAuditSnapshot before = FlagAuditSnapshot.from(
                existing
        );

        return auditContext.within(
                new FlagAuditContext.AuditDetails(
                        actor,
                        before
                ),
                () -> flagService.deleteFlag(id)
        );
    }

    public FeatureFlag toggleFlag(Long id, FlagToggleRequest request, String actor) {
        FeatureFlag existing = flagService.getById(id);
        permissionService.validateMutationPermission(existing.getEnvironment());
        if (request == null) {
            throw new InvalidOperationException("Request body is required");
        }
        if (request.getEnabled() == null) {
            throw new InvalidOperationException("enabled is required");
        }
        if (request.getExpectedVersion() == null) {
            throw new InvalidOperationException("expectedVersion is required when toggling a flag");
        }
        if (request.getExpectedVersion() < 0) {
            throw new InvalidOperationException("expectedVersion cannot be negative");
        }
        if (!Objects.equals(existing.getVersion(), request.getExpectedVersion())) {
            throw new ObjectOptimisticLockingFailureException(FeatureFlag.class, id);
        }

        if (Objects.equals(existing.getEnabled(), request.getEnabled())) {
            return existing;
        }

        FlagAuditSnapshot before = FlagAuditSnapshot.from(
                existing
        );

        return auditContext.within(
                new FlagAuditContext.AuditDetails(
                        actor,
                        before
                ),
                () -> flagService.setEnabledState(id, request.getEnabled(), request.getExpectedVersion())
        );
    }
}
