package com.featureflag.flag_service.service;

import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.event.FlagAuditSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlagMutationAuditServiceTest {

    @Mock
    private FlagService flagService;

    private FlagAuditContext auditContext;
    private FlagMutationAuditService service;

    @BeforeEach
    void setUp() {
        auditContext = new FlagAuditContext();
        service = new FlagMutationAuditService(
                flagService,
                auditContext
        );
    }

    @Test
    void createRunsWithActorAndNullBeforeState() {
        FlagRequest request = new FlagRequest();
        request.setFlagKey("CHECKOUT");
        FeatureFlag created = flag(true);
        when(flagService.createFlag(request)).thenAnswer(invocation -> {
            assertThat(request.getFlagKey()).isEqualTo("checkout");
            assertContext("actor-123", null);
            return created;
        });

        assertThat(service.createFlag(request, " actor-123 "))
                .isSameAs(created);
        assertThat(auditContext.current()).isEmpty();
    }

    @Test
    void updateCapturesImmutableBeforeStateBeforeDelegating() {
        FlagRequest request = new FlagRequest();
        request.setExpectedVersion(0L);
        request.setFlagKey("CHECKOUT");
        FeatureFlag existing = flag(false);
        FeatureFlag updated = flag(true);
        when(flagService.getById(10L)).thenReturn(existing);
        when(flagService.updateFlag(10L, request))
                .thenAnswer(invocation -> {
                    assertThat(request.getFlagKey()).isEqualTo(existing.getFlagKey());
                    assertContext(
                            "actor-123",
                            FlagAuditSnapshot.from(existing)
                    );
                    return updated;
                });

        assertThat(service.updateFlag(
                10L,
                request,
                "actor-123"
        )).isSameAs(updated);
        assertThat(auditContext.current()).isEmpty();
    }

    @Test
    void staleBrowserEditIsRejectedBeforeMutation() {
        FeatureFlag existing = flag(false);
        existing.setVersion(2L);
        when(flagService.getById(10L)).thenReturn(existing);
        FlagRequest request = new FlagRequest();
        request.setExpectedVersion(1L);
        request.setFlagKey("checkout");

        assertThatThrownBy(() -> service.updateFlag(10L, request, "actor"))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        org.mockito.Mockito.verify(flagService, org.mockito.Mockito.never())
                .updateFlag(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        assertThat(existing.getEnabled()).isFalse();
    }

    @Test
    void missingBrowserVersionIsAClientError() {
        when(flagService.getById(10L)).thenReturn(flag(false));
        assertThatThrownBy(() -> service.updateFlag(10L, new FlagRequest(), "actor"))
                .isInstanceOf(com.featureflag.flag_service.exception.InvalidOperationException.class)
                .hasMessageContaining("expectedVersion");
    }

    @Test
    void caseOnlyEditPreservesLegacyRolloutSeed() {
        FeatureFlag existing = flag(true);
        existing.setFlagKey("CHECKOUT");
        when(flagService.getById(10L)).thenReturn(existing);
        FlagRequest request = new FlagRequest();
        request.setFlagKey("Checkout");
        request.setExpectedVersion(0L);
        when(flagService.updateFlag(10L, request)).thenReturn(existing);
        service.updateFlag(10L, request, "actor");
        assertThat(request.getFlagKey()).isEqualTo("CHECKOUT");
    }

    @Test
    void toggleCapturesPreviousEnabledValue() {
        FeatureFlag existing = flag(false);
        FeatureFlag toggled = flag(true);
        when(flagService.getById(10L)).thenReturn(existing);
        when(flagService.toggleFlag(10L)).thenAnswer(invocation -> {
            assertThat(
                    auditContext.current().orElseThrow()
                            .before().enabled()
            ).isFalse();
            return toggled;
        });

        assertThat(service.toggleFlag(10L, "actor-123"))
                .isSameAs(toggled);
        assertThat(auditContext.current()).isEmpty();
    }

    @Test
    void deleteCapturesDeletedState() {
        FeatureFlag existing = flag(true);
        when(flagService.getById(10L)).thenReturn(existing);
        when(flagService.deleteFlag(10L)).thenAnswer(invocation -> {
            assertThat(
                    auditContext.current().orElseThrow()
                            .before().id()
            ).isEqualTo(10L);
            return "deleted";
        });

        assertThat(service.deleteFlag(10L, "actor-123"))
                .isEqualTo("deleted");
        assertThat(auditContext.current()).isEmpty();
    }

    @Test
    void contextIsClearedWhenMutationFails() {
        FlagRequest request = new FlagRequest();
        request.setFlagKey("checkout");
        when(flagService.createFlag(request))
                .thenThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(
                () -> service.createFlag(request, "actor-123")
        ).isInstanceOf(IllegalStateException.class);
        assertThat(auditContext.current()).isEmpty();
    }

    private void assertContext(
            String actor,
            FlagAuditSnapshot before
    ) {
        FlagAuditContext.AuditDetails details =
                auditContext.current().orElseThrow();
        assertThat(details.actor()).isEqualTo(actor);
        assertThat(details.before()).isEqualTo(before);
    }

    private FeatureFlag flag(boolean enabled) {
        return FeatureFlag.builder()
                .id(10L)
                .version(0L)
                .flagKey("checkout")
                .name("Checkout")
                .environment("DEV")
                .enabled(enabled)
                .rolloutPercentage(50)
                .targetUsers(List.of("user-1"))
                .build();
    }
}
