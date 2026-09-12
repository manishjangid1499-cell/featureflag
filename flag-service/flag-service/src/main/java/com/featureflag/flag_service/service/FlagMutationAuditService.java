package com.featureflag.flag_service.service;

import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.event.FlagAuditSnapshot;
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

    public FeatureFlag createFlag(
            FlagRequest request,
            String actor
    ) {
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
        if (request.getExpectedVersion() == null) {
            throw new InvalidOperationException("expectedVersion is required when updating a flag");
        }
        if (!Objects.equals(existing.getVersion(), request.getExpectedVersion())) {
            throw new ObjectOptimisticLockingFailureException(FeatureFlag.class, id);
        }
        // Preserve legacy canonical spelling so a case-only edit cannot reshuffle cohorts.
        request.setFlagKey(existing.getFlagKey().equalsIgnoreCase(request.getFlagKey())
                ? existing.getFlagKey() : request.getFlagKey().toLowerCase(Locale.ROOT));
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
        FlagAuditSnapshot before = FlagAuditSnapshot.from(
                flagService.getById(id)
        );

        return auditContext.within(
                new FlagAuditContext.AuditDetails(
                        actor,
                        before
                ),
                () -> flagService.deleteFlag(id)
        );
    }

    public FeatureFlag toggleFlag(Long id, String actor) {
        FlagAuditSnapshot before = FlagAuditSnapshot.from(
                flagService.getById(id)
        );

        return auditContext.within(
                new FlagAuditContext.AuditDetails(
                        actor,
                        before
                ),
                () -> flagService.toggleFlag(id)
        );
    }
}
