package com.featureflag.flag_service.service;

import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.event.FlagAuditSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        FlagAuditSnapshot before = FlagAuditSnapshot.from(
                flagService.getById(id)
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
