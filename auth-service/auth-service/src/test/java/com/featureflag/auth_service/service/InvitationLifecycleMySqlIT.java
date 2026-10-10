package com.featureflag.auth_service.service;

import com.featureflag.auth_service.client.NotificationClient;
import com.featureflag.auth_service.dto.*;
import com.featureflag.auth_service.entity.*;
import com.featureflag.auth_service.exception.ForbiddenException;
import com.featureflag.auth_service.exception.InvalidOperationException;
import com.featureflag.auth_service.exception.InvitationConflictException;
import com.featureflag.auth_service.repository.InvitationRepository;
import com.featureflag.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "NOTIFICATION_INTERNAL_SERVICE_KEY=invitation-test-key")
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({InvitationService.class, InvitationNotificationDispatcher.class,
        InvitationLifecycleMySqlIT.TestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InvitationLifecycleMySqlIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11");

    @Autowired private InvitationService invitations;
    @Autowired private InvitationRepository invitationRepository;
    @Autowired private UserRepository userRepository;
    @MockitoBean private NotificationClient client;

    private static final AtomicReference<Instant> FIXED_NOW =
            new AtomicReference<>(Instant.parse("2026-10-01T12:00:00Z"));

    @TestConfiguration
    static class TestConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        Clock clock() {
            return new Clock() {
                @Override
                public java.time.ZoneId getZone() {
                    return java.time.ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(java.time.ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return FIXED_NOW.get();
                }
            };
        }
    }

    @BeforeEach
    void cleanDatabase() {
        FIXED_NOW.set(Instant.parse("2026-10-01T12:00:00Z"));
        invitationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentAcceptancePermitsOnlyOneSuccessfulAcceptanceAndAccountInMySql() throws Exception {
        User owner = ownerUser();
        var inviteResponse = invitations.inviteMember(
                new InviteMemberRequest("Concurrent Candidate", "concurrent@example.test", Role.DEVELOPER),
                owner
        );

        ArgumentCaptor<InvitationNotificationDto> captor = ArgumentCaptor.forClass(InvitationNotificationDto.class);
        verify(client, atLeastOnce()).sendInvitationEmail(anyString(), captor.capture());
        String token = extractToken(captor.getValue());

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Future<String>> futures = new ArrayList<>();
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                try {
                    return invitations.acceptInvitation(
                            new AcceptInvitationRequest(token, "SecurePass123!", "SecurePass123!")
                    );
                } catch (Throwable t) {
                    exceptions.add(t);
                    throw t;
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        int successes = 0;
        int conflicts = 0;
        for (Future<String> future : futures) {
            try {
                String result = future.get();
                assertNotNull(result);
                successes++;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof InvitationConflictException) {
                    conflicts++;
                }
            }
        }

        assertEquals(1, successes, "Exactly one concurrent acceptance must succeed");
        assertEquals(1, conflicts, "The other concurrent acceptance must be rejected with conflict");

        // Verify state in MySQL: exactly one user account created
        assertEquals(1, userRepository.count());
        User createdUser = userRepository.findByEmail("concurrent@example.test").orElseThrow();
        assertEquals("Concurrent Candidate", createdUser.getName());
        assertEquals(Role.DEVELOPER, createdUser.getRole());

        // Verify invitation in MySQL is ACCEPTED
        Invitation inDb = invitationRepository.findById(inviteResponse.getId()).orElseThrow();
        assertEquals(InvitationStatus.ACCEPTED, inDb.getStatus());
        assertNotNull(inDb.getAcceptedAt());
    }

    @Test
    void validateExpiredPendingInvitationReportsExpiredWithoutPersistedMutationInMySql() {
        User owner = ownerUser();
        var invite = invitations.inviteMember(
                new InviteMemberRequest("Candidate", "expire-validate@example.test", Role.DEVELOPER),
                owner
        );

        ArgumentCaptor<InvitationNotificationDto> captor = ArgumentCaptor.forClass(InvitationNotificationDto.class);
        verify(client, atLeastOnce()).sendInvitationEmail(anyString(), captor.capture());
        String token = extractToken(captor.getValue());

        // Fast forward clock past expiresAt
        FIXED_NOW.set(invite.getExpiresAt().plusSeconds(3600));

        ValidateInvitationResponse res = invitations.validateInvitation(token);
        assertFalse(res.isValid());
        assertTrue(res.getErrorMessage().contains("expired"));

        // Verify MySQL database record has NOT been mutated to EXPIRED
        Invitation inDb = invitationRepository.findById(invite.getId()).orElseThrow();
        assertEquals(InvitationStatus.PENDING, inDb.getStatus(), "Database status in MySQL must remain PENDING");
    }

    @Test
    void listExpiredPendingInvitationPresentsExpiredWithoutPersistedMutationInMySql() {
        User owner = ownerUser();
        var invite = invitations.inviteMember(
                new InviteMemberRequest("Candidate", "expire-list@example.test", Role.DEVELOPER),
                owner
        );

        // Fast forward clock past expiresAt
        FIXED_NOW.set(invite.getExpiresAt().plusSeconds(3600));

        var page = invitations.getAllInvitations(Pageable.unpaged());
        var listed = page.getContent().stream()
                .filter(i -> i.getId().equals(invite.getId()))
                .findFirst().orElseThrow();
        assertEquals(InvitationStatus.EXPIRED, listed.getStatus(), "Listed response must present effective status EXPIRED");

        // Verify MySQL database record has NOT been mutated to EXPIRED
        Invitation inDb = invitationRepository.findById(invite.getId()).orElseThrow();
        assertEquals(InvitationStatus.PENDING, inDb.getStatus(), "Database status in MySQL must remain PENDING");
    }

    @Test
    void acceptedAndRevokedInvitationsRetainStateWhenListedAndValidatedInMySql() {
        User owner = ownerUser();
        var invite = invitations.inviteMember(
                new InviteMemberRequest("Candidate", "state-retain@example.test", Role.DEVELOPER),
                owner
        );

        ArgumentCaptor<InvitationNotificationDto> captor = ArgumentCaptor.forClass(InvitationNotificationDto.class);
        verify(client, atLeastOnce()).sendInvitationEmail(anyString(), captor.capture());
        String token = extractToken(captor.getValue());

        // Accept invitation
        invitations.acceptInvitation(new AcceptInvitationRequest(token, "SecurePass123!", "SecurePass123!"));

        // Fast forward clock past expiresAt
        FIXED_NOW.set(invite.getExpiresAt().plusSeconds(3600));

        // Validate acceptance url after expiry: must report already accepted, not expired
        ValidateInvitationResponse validateRes = invitations.validateInvitation(token);
        assertFalse(validateRes.isValid());
        assertTrue(validateRes.getErrorMessage().contains("already been accepted"));

        // List invitations: must show ACCEPTED
        var listed = invitations.getAllInvitations(Pageable.unpaged()).getContent().stream()
                .filter(i -> i.getId().equals(invite.getId()))
                .findFirst().orElseThrow();
        assertEquals(InvitationStatus.ACCEPTED, listed.getStatus());

        Invitation inDb = invitationRepository.findById(invite.getId()).orElseThrow();
        assertEquals(InvitationStatus.ACCEPTED, inDb.getStatus());
    }

    @Test
    void acceptanceAfterExpiryIsRejectedInMySql() {
        User owner = ownerUser();
        var invite = invitations.inviteMember(
                new InviteMemberRequest("Candidate", "after-expiry@example.test", Role.DEVELOPER),
                owner
        );

        ArgumentCaptor<InvitationNotificationDto> captor = ArgumentCaptor.forClass(InvitationNotificationDto.class);
        verify(client, atLeastOnce()).sendInvitationEmail(anyString(), captor.capture());
        String token = extractToken(captor.getValue());

        // Fast forward clock past expiresAt
        FIXED_NOW.set(invite.getExpiresAt().plusMillis(1));

        InvalidOperationException ex = assertThrows(
                InvalidOperationException.class,
                () -> invitations.acceptInvitation(new AcceptInvitationRequest(token, "SecurePass123!", "SecurePass123!"))
        );
        assertTrue(ex.getMessage().contains("expired"));

        // No user created
        assertEquals(0, userRepository.count());
        // Status remains PENDING in MySQL
        Invitation inDb = invitationRepository.findById(invite.getId()).orElseThrow();
        assertEquals(InvitationStatus.PENDING, inDb.getStatus());
    }

    @Test
    void acceptanceAtExactExpiryBoundarySucceedsInMySql() {
        User owner = ownerUser();
        var invite = invitations.inviteMember(
                new InviteMemberRequest("Boundary User", "boundary@example.test", Role.DEVELOPER),
                owner
        );

        ArgumentCaptor<InvitationNotificationDto> captor = ArgumentCaptor.forClass(InvitationNotificationDto.class);
        verify(client, atLeastOnce()).sendInvitationEmail(anyString(), captor.capture());
        String token = extractToken(captor.getValue());

        // Set clock to EXACT expiresAt boundary
        FIXED_NOW.set(invite.getExpiresAt());

        String result = invitations.acceptInvitation(
                new AcceptInvitationRequest(token, "SecurePass123!", "SecurePass123!")
        );
        assertNotNull(result);
        assertTrue(result.contains("successfully"));

        assertEquals(1, userRepository.count());
        Invitation inDb = invitationRepository.findById(invite.getId()).orElseThrow();
        assertEquals(InvitationStatus.ACCEPTED, inDb.getStatus());
    }

    @Test
    void replacementPermissionProtectionPreservedInMySql() {
        User owner = ownerUser();
        User admin = User.builder().id(2L).name("Admin").email("admin@example.test").role(Role.ADMIN).build();

        var adminInvite = invitations.inviteMember(
                new InviteMemberRequest("Admin Candidate", "admin-replace@example.test", Role.ADMIN),
                owner
        );
        assertEquals(Role.ADMIN, adminInvite.getInvitedRole());

        // Admin cannot replace with VIEWER
        assertThrows(ForbiddenException.class, () ->
                invitations.inviteMember(
                        new InviteMemberRequest("Viewer Candidate", "admin-replace@example.test", Role.VIEWER),
                        admin
                )
        );

        // Invitation remains PENDING ADMIN in MySQL
        Invitation inDb = invitationRepository.findById(adminInvite.getId()).orElseThrow();
        assertEquals(InvitationStatus.PENDING, inDb.getStatus());
        assertEquals(Role.ADMIN, inDb.getInvitedRole());
    }

    private User ownerUser() {
        return User.builder().id(1L).name("Owner").email("owner@example.test").role(Role.OWNER).build();
    }

    private String extractToken(InvitationNotificationDto request) {
        return URI.create(request.getAcceptanceUrl()).getRawQuery().substring("token=".length());
    }
}
