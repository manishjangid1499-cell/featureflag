package com.featureflag.auth_service.service;

import com.featureflag.auth_service.config.OwnerBootstrapProperties;
import com.featureflag.auth_service.config.OwnerBootstrapService;
import com.featureflag.auth_service.dto.TransferOwnershipRequest;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.SystemInvariant;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.exception.ForbiddenException;
import com.featureflag.auth_service.exception.InvalidOperationException;
import com.featureflag.auth_service.repository.SystemInvariantRepository;
import com.featureflag.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({MemberService.class, OwnershipTransferMySqlIT.TestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OwnershipTransferMySqlIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11");

    @TestConfiguration
    static class TestConfig {
        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }

    @Autowired private UserRepository userRepository;
    @Autowired private SystemInvariantRepository systemInvariantRepository;
    @Autowired private MemberService memberService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void cleanUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            userRepository.deleteAll();
            SystemInvariant inv = systemInvariantRepository.findById(SystemInvariant.OWNER_INSTALLATION)
                    .orElseGet(() -> SystemInvariant.builder()
                            .name(SystemInvariant.OWNER_INSTALLATION)
                            .updatedAt(java.time.Instant.now())
                            .build());
            inv.setOwnerUserId(null);
            inv.setUpdatedAt(java.time.Instant.now());
            systemInvariantRepository.save(inv);
        });
    }

    @Test
    @DisplayName("Concurrent bootstrap from multiple threads creates exactly 1 OWNER")
    void concurrentBootstrapCreatesSingleOwner() throws Exception {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                OwnerBootstrapProperties props = new OwnerBootstrapProperties();
                props.setEnabled(true);
                props.setName("Owner " + index);
                props.setEmail("owner" + index + "@company.test");
                props.setPassword("StrongOwnerPassword123!");

                // Run bootstrap inside its own transaction
                Boolean executed = tx.execute(status -> {
                    OwnerBootstrapService bootstrapService = new OwnerBootstrapService(
                            userRepository, systemInvariantRepository, passwordEncoder, props
                    );
                    bootstrapService.run(new DefaultApplicationArguments());
                    return true;
                });
                return executed;
            }));
        }

        ready.await();
        start.countDown();

        for (Future<Boolean> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        tx.executeWithoutResult(status -> {
            long ownerCount = userRepository.countByRole(Role.OWNER);
            assertThat(ownerCount).isEqualTo(1);

            SystemInvariant invariant = systemInvariantRepository
                    .findById(SystemInvariant.OWNER_INSTALLATION)
                    .orElseThrow();
            assertThat(invariant.getOwnerUserId()).isNotNull();

            User owner = userRepository.findById(invariant.getOwnerUserId()).orElseThrow();
            assertThat(owner.getRole()).isEqualTo(Role.OWNER);
        });
    }

    @Test
    @DisplayName("Existing valid owner makes bootstrap a no-op")
    void existingValidOwnerMakesBootstrapNoOp() {
        User existingOwner = tx.execute(status -> userRepository.save(User.builder()
                .name("Pre-existing Owner")
                .email("original@company.test")
                .password(passwordEncoder.encode("ExistingPass123!"))
                .role(Role.OWNER)
                .build()));

        OwnerBootstrapProperties props = new OwnerBootstrapProperties();
        props.setEnabled(true);
        props.setName("New Intruder");
        props.setEmail("intruder@company.test");
        props.setPassword("IntruderPass123!");

        tx.executeWithoutResult(status -> {
            OwnerBootstrapService bootstrapService = new OwnerBootstrapService(
                    userRepository, systemInvariantRepository, passwordEncoder, props
            );
            bootstrapService.run(new DefaultApplicationArguments());
        });

        tx.executeWithoutResult(status -> {
            assertThat(userRepository.countByRole(Role.OWNER)).isEqualTo(1);
            assertThat(userRepository.findByEmail("intruder@company.test")).isEmpty();
            User currentOwner = userRepository.findByEmail("original@company.test").orElseThrow();
            assertThat(currentOwner.getId()).isEqualTo(existingOwner.getId());
        });
    }

    @Test
    @DisplayName("Multiple OWNER accounts in DB causes bootstrap to abort without mutating")
    void multiOwnerInconsistencyAbortsBootstrap() {
        tx.executeWithoutResult(status -> {
            userRepository.save(User.builder()
                    .name("Owner 1").email("o1@company.test")
                    .password(passwordEncoder.encode("pass1")).role(Role.OWNER).build());
            userRepository.save(User.builder()
                    .name("Owner 2").email("o2@company.test")
                    .password(passwordEncoder.encode("pass2")).role(Role.OWNER).build());
        });

        OwnerBootstrapProperties props = new OwnerBootstrapProperties();
        props.setEnabled(true);
        props.setName("Owner 3");
        props.setEmail("o3@company.test");
        props.setPassword("pass3Password123!");

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            OwnerBootstrapService bootstrapService = new OwnerBootstrapService(
                    userRepository, systemInvariantRepository, passwordEncoder, props
            );
            bootstrapService.run(new DefaultApplicationArguments());
        })).isInstanceOf(IllegalStateException.class)
           .hasMessageContaining("Multiple OWNER accounts detected");
    }

    @Test
    @DisplayName("Successful ownership transfer demotes caller to ADMIN, promotes target to OWNER, increments credentialVersions")
    void successfulOwnershipTransfer() {
        Long[] ids = tx.execute(status -> {
            User owner = userRepository.save(User.builder()
                    .name("Initial Owner").email("owner@company.test")
                    .password(passwordEncoder.encode("OwnerCurrentPass123!"))
                    .role(Role.OWNER).credentialVersion(1L).build());
            User admin = userRepository.save(User.builder()
                    .name("Target Admin").email("admin@company.test")
                    .password(passwordEncoder.encode("AdminPass123!"))
                    .role(Role.ADMIN).credentialVersion(3L).build());
            return new Long[]{owner.getId(), admin.getId()};
        });
        Long ownerId = ids[0];
        Long adminId = ids[1];

        User callerAuth = tx.execute(status -> userRepository.findById(ownerId).orElseThrow());

        TransferOwnershipRequest request = new TransferOwnershipRequest(adminId, "OwnerCurrentPass123!");

        tx.executeWithoutResult(status -> {
            memberService.transferOwnership(request, callerAuth);
        });

        tx.executeWithoutResult(status -> {
            User oldOwner = userRepository.findById(ownerId).orElseThrow();
            User newOwner = userRepository.findById(adminId).orElseThrow();

            assertThat(oldOwner.getRole()).isEqualTo(Role.ADMIN);
            assertThat(oldOwner.getCredentialVersion()).isEqualTo(2L);

            assertThat(newOwner.getRole()).isEqualTo(Role.OWNER);
            assertThat(newOwner.getCredentialVersion()).isEqualTo(4L);

            assertThat(userRepository.countByRole(Role.OWNER)).isEqualTo(1);

            SystemInvariant invariant = systemInvariantRepository
                    .findById(SystemInvariant.OWNER_INSTALLATION)
                    .orElseThrow();
            assertThat(invariant.getOwnerUserId()).isEqualTo(newOwner.getId());
        });
    }

    @Test
    @DisplayName("Ownership transfer rejected with invalid password without mutating accounts")
    void invalidPasswordRejectsTransfer() {
        Long[] ids = tx.execute(status -> {
            User owner = userRepository.save(User.builder()
                    .name("Owner").email("owner@company.test")
                    .password(passwordEncoder.encode("CorrectPass123!"))
                    .role(Role.OWNER).credentialVersion(1L).build());
            User admin = userRepository.save(User.builder()
                    .name("Admin").email("admin@company.test")
                    .password(passwordEncoder.encode("AdminPass123!"))
                    .role(Role.ADMIN).credentialVersion(1L).build());
            return new Long[]{owner.getId(), admin.getId()};
        });

        User callerAuth = tx.execute(status -> userRepository.findById(ids[0]).orElseThrow());
        TransferOwnershipRequest badPassReq = new TransferOwnershipRequest(ids[1], "WrongPassword!");

        assertThatThrownBy(() -> memberService.transferOwnership(badPassReq, callerAuth))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Current password does not match");

        tx.executeWithoutResult(status -> {
            User owner = userRepository.findById(ids[0]).orElseThrow();
            User admin = userRepository.findById(ids[1]).orElseThrow();
            assertThat(owner.getRole()).isEqualTo(Role.OWNER);
            assertThat(owner.getCredentialVersion()).isEqualTo(1L);
            assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
            assertThat(admin.getCredentialVersion()).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("Ownership transfer cannot target self, DEVELOPER, VIEWER, or disabled account")
    void nonAdminOrDisabledTargetRejected() {
        Long[] ids = tx.execute(status -> {
            User owner = userRepository.save(User.builder()
                    .name("Owner").email("owner@company.test")
                    .password(passwordEncoder.encode("OwnerPass123!"))
                    .role(Role.OWNER).build());
            User dev = userRepository.save(User.builder()
                    .name("Dev").email("dev@company.test")
                    .password(passwordEncoder.encode("pass")).role(Role.DEVELOPER).build());
            User disabledAdmin = userRepository.save(User.builder()
                    .name("Disabled Admin").email("disabled@company.test")
                    .password(passwordEncoder.encode("pass")).role(Role.ADMIN).enabled(false).build());
            return new Long[]{owner.getId(), dev.getId(), disabledAdmin.getId()};
        });

        User callerAuth = tx.execute(status -> userRepository.findById(ids[0]).orElseThrow());

        // Self
        assertThatThrownBy(() -> memberService.transferOwnership(new TransferOwnershipRequest(ids[0], "OwnerPass123!"), callerAuth))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot transfer ownership to yourself");

        // Developer
        assertThatThrownBy(() -> memberService.transferOwnership(new TransferOwnershipRequest(ids[1], "OwnerPass123!"), callerAuth))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Ownership can only be transferred to an active ADMIN");

        // Disabled Admin
        assertThatThrownBy(() -> memberService.transferOwnership(new TransferOwnershipRequest(ids[2], "OwnerPass123!"), callerAuth))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Cannot transfer ownership to a disabled account");
    }

    @Test
    @DisplayName("General member operations cannot demote, disable, or delete OWNER")
    void generalMemberOperationsCannotMutateOwner() {
        Long[] ids = tx.execute(status -> {
            User owner = userRepository.save(User.builder()
                    .name("Owner").email("owner@company.test")
                    .password(passwordEncoder.encode("pass")).role(Role.OWNER).build());
            User admin = userRepository.save(User.builder()
                    .name("Admin").email("admin@company.test")
                    .password(passwordEncoder.encode("pass")).role(Role.ADMIN).build());
            return new Long[]{owner.getId(), admin.getId()};
        });

        User adminAuth = tx.execute(status -> userRepository.findById(ids[1]).orElseThrow());

        // Update role
        assertThatThrownBy(() -> memberService.updateRole(ids[0], Role.DEVELOPER, adminAuth))
                .isInstanceOf(InvalidOperationException.class);

        // Update status
        assertThatThrownBy(() -> memberService.updateEnabled(ids[0], false, adminAuth))
                .isInstanceOf(InvalidOperationException.class);

        // Delete member
        assertThatThrownBy(() -> memberService.deleteMember(ids[0], adminAuth))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("Concurrent transfer vs disable/delete race preserves single active OWNER")
    void concurrentTransferVsMemberMutationRace() throws Exception {
        Long[] ids = tx.execute(status -> {
            User owner = userRepository.save(User.builder()
                    .name("Owner").email("owner@race.test")
                    .password(passwordEncoder.encode("OwnerPass123!"))
                    .role(Role.OWNER).credentialVersion(1L).build());
            User admin = userRepository.save(User.builder()
                    .name("Admin").email("admin@race.test")
                    .password(passwordEncoder.encode("AdminPass123!"))
                    .role(Role.ADMIN).credentialVersion(1L).build());
            return new Long[]{owner.getId(), admin.getId()};
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> transferFuture = executor.submit(() -> {
            ready.countDown();
            start.await();
            try {
                tx.executeWithoutResult(status -> {
                    User ownerAuth = userRepository.findById(ids[0]).orElseThrow();
                    memberService.transferOwnership(new TransferOwnershipRequest(ids[1], "OwnerPass123!"), ownerAuth);
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        Future<Boolean> disableFuture = executor.submit(() -> {
            ready.countDown();
            start.await();
            try {
                tx.executeWithoutResult(status -> {
                    User ownerAuth = userRepository.findById(ids[0]).orElseThrow();
                    memberService.updateEnabled(ids[1], false, ownerAuth);
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        ready.await();
        start.countDown();

        boolean transferOk = transferFuture.get(10, TimeUnit.SECONDS);
        boolean disableOk = disableFuture.get(10, TimeUnit.SECONDS);

        executor.shutdown();

        tx.executeWithoutResult(status -> {
            long ownerCount = userRepository.countByRole(Role.OWNER);
            assertThat(ownerCount).isEqualTo(1);

            User currentOwner = userRepository.findAllByRole(Role.OWNER).get(0);
            assertThat(currentOwner.isEnabled()).isTrue();
        });
    }

    @Test
    @DisplayName("Two simultaneous transfer requests from same owner to different admins: exactly 1 succeeds and 1 active OWNER remains")
    void twoSimultaneousTransfersToOneWinner() throws Exception {
        Long[] ids = tx.execute(status -> {
            User owner = userRepository.save(User.builder()
                    .name("Owner").email("owner@dual-transfer.test")
                    .password(passwordEncoder.encode("OwnerPass123!"))
                    .role(Role.OWNER).credentialVersion(1L).build());
            User admin1 = userRepository.save(User.builder()
                    .name("Admin 1").email("admin1@dual-transfer.test")
                    .password(passwordEncoder.encode("AdminPass123!"))
                    .role(Role.ADMIN).credentialVersion(1L).build());
            User admin2 = userRepository.save(User.builder()
                    .name("Admin 2").email("admin2@dual-transfer.test")
                    .password(passwordEncoder.encode("AdminPass123!"))
                    .role(Role.ADMIN).credentialVersion(1L).build());
            return new Long[]{owner.getId(), admin1.getId(), admin2.getId()};
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> transfer1 = executor.submit(() -> {
            ready.countDown();
            start.await();
            try {
                tx.executeWithoutResult(status -> {
                    User ownerAuth = userRepository.findById(ids[0]).orElseThrow();
                    memberService.transferOwnership(new TransferOwnershipRequest(ids[1], "OwnerPass123!"), ownerAuth);
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        Future<Boolean> transfer2 = executor.submit(() -> {
            ready.countDown();
            start.await();
            try {
                tx.executeWithoutResult(status -> {
                    User ownerAuth = userRepository.findById(ids[0]).orElseThrow();
                    memberService.transferOwnership(new TransferOwnershipRequest(ids[2], "OwnerPass123!"), ownerAuth);
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        ready.await();
        start.countDown();

        boolean t1Ok = transfer1.get(10, TimeUnit.SECONDS);
        boolean t2Ok = transfer2.get(10, TimeUnit.SECONDS);

        executor.shutdown();

        // Exactly one transfer succeeded
        assertThat(t1Ok ^ t2Ok).isTrue();

        tx.executeWithoutResult(status -> {
            assertThat(userRepository.countByRole(Role.OWNER)).isEqualTo(1);
            User activeOwner = userRepository.findAllByRole(Role.OWNER).get(0);
            assertThat(activeOwner.isEnabled()).isTrue();
            assertThat(List.of(ids[1], ids[2])).contains(activeOwner.getId());

            User oldOwner = userRepository.findById(ids[0]).orElseThrow();
            assertThat(oldOwner.getRole()).isEqualTo(Role.ADMIN);

            SystemInvariant invariant = systemInvariantRepository.findById(SystemInvariant.OWNER_INSTALLATION).orElseThrow();
            assertThat(invariant.getOwnerUserId()).isEqualTo(activeOwner.getId());
        });
    }

    @Test
    @DisplayName("Caller demoted or credential changed before transfer rechecks authority under lock and aborts")
    void callerDemotedBeforeTransferAbortsUnderLock() {
        Long[] ids = tx.execute(status -> {
            User owner = userRepository.save(User.builder()
                    .name("Owner").email("owner@stale-auth.test")
                    .password(passwordEncoder.encode("OwnerPass123!"))
                    .role(Role.OWNER).credentialVersion(1L).build());
            User admin = userRepository.save(User.builder()
                    .name("Admin").email("admin@stale-auth.test")
                    .password(passwordEncoder.encode("AdminPass123!"))
                    .role(Role.ADMIN).credentialVersion(1L).build());
            return new Long[]{owner.getId(), admin.getId()};
        });

        User staleOwnerCaller = tx.execute(status -> userRepository.findById(ids[0]).orElseThrow());

        // Now demote caller in DB
        tx.executeWithoutResult(status -> {
            User owner = userRepository.findById(ids[0]).orElseThrow();
            owner.setRole(Role.ADMIN);
            owner.setCredentialVersion(2L);
            userRepository.save(owner);
        });

        TransferOwnershipRequest request = new TransferOwnershipRequest(ids[1], "OwnerPass123!");

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> memberService.transferOwnership(request, staleOwnerCaller)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only the current OWNER can transfer ownership");

        tx.executeWithoutResult(status -> {
            User admin = userRepository.findById(ids[1]).orElseThrow();
            assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
            assertThat(userRepository.countByRole(Role.OWNER)).isEqualTo(0);
        });
    }
}
