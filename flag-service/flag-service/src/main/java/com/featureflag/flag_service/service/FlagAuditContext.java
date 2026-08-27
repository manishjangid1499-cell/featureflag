package com.featureflag.flag_service.service;

import com.featureflag.flag_service.event.FlagAuditSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

@Component
public class FlagAuditContext {

    private final ThreadLocal<AuditDetails> current =
            new ThreadLocal<>();

    public <T> T within(
            AuditDetails details,
            Supplier<T> operation
    ) {
        if (current.get() != null) {
            throw new IllegalStateException(
                    "A flag audit context is already active"
            );
        }

        current.set(details);
        try {
            return operation.get();
        } finally {
            current.remove();
        }
    }

    public Optional<AuditDetails> current() {
        return Optional.ofNullable(current.get());
    }

    public record AuditDetails(
            String actor,
            FlagAuditSnapshot before
    ) {

        public AuditDetails {
            actor = actor == null || actor.isBlank()
                    ? null
                    : actor.trim();
        }
    }
}
