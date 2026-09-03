package com.featureflag.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role invitedRole;

    private Long invitedByUserId;

    private String invitedByEmail;

    private String invitedByName;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    private Instant acceptedAt;

    @Column(nullable = false)
    private Instant createdAt;
}
