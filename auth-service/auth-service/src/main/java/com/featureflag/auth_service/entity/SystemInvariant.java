package com.featureflag.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "system_invariants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemInvariant {

    public static final String OWNER_INSTALLATION = "OWNER_INSTALLATION";

    @Id
    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }
}
