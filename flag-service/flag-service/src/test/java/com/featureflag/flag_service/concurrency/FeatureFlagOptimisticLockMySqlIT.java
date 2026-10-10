package com.featureflag.flag_service.concurrency;

import com.featureflag.flag_service.entity.FeatureFlag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.TimeZone;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class FeatureFlagOptimisticLockMySqlIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11");

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void staleTransactionCannotSilentlyOverwriteCommittedUpdate() {
        Long flagId = insertFlag();

        EntityManager firstManager =
                entityManagerFactory.createEntityManager();
        EntityManager staleManager =
                entityManagerFactory.createEntityManager();
        try {
            firstManager.getTransaction().begin();
            staleManager.getTransaction().begin();

            FeatureFlag first = firstManager.find(
                    FeatureFlag.class,
                    flagId
            );
            FeatureFlag stale = staleManager.find(
                    FeatureFlag.class,
                    flagId
            );

            assertThat(first.getVersion()).isZero();
            assertThat(stale.getVersion()).isZero();

            first.setDescription("committed by transaction A");
            firstManager.getTransaction().commit();

            stale.setDescription("stale transaction B overwrite");
            assertThrows(
                    RollbackException.class,
                    () -> staleManager.getTransaction().commit()
            );
        } finally {
            if (firstManager.getTransaction().isActive()) {
                firstManager.getTransaction().rollback();
            }
            if (staleManager.getTransaction().isActive()) {
                staleManager.getTransaction().rollback();
            }
            firstManager.close();
            staleManager.close();
        }

        EntityManager verifier =
                entityManagerFactory.createEntityManager();
        try {
            FeatureFlag persisted = verifier.find(
                    FeatureFlag.class,
                    flagId
            );
            assertThat(persisted.getDescription())
                    .isEqualTo("committed by transaction A");
            assertThat(persisted.getVersion()).isEqualTo(1L);
        } finally {
            verifier.close();
        }
    }

    private Long insertFlag() {
        return insertFlag("concurrent-" + UUID.randomUUID());
    }

    private Long insertFlag(String flagKey) {
        EntityManager manager =
                entityManagerFactory.createEntityManager();
        try {
            manager.getTransaction().begin();
            FeatureFlag flag = FeatureFlag.builder()
                    .name("Concurrent checkout")
                    .flagKey(flagKey)
                    .environment("DEV")
                    .enabled(false)
                    .rolloutPercentage(0)
                    .description("initial")
                    .build();
            manager.persist(flag);
            manager.getTransaction().commit();
            assertThat(flag.getVersion()).isZero();
            return flag.getId();
        } finally {
            if (manager.getTransaction().isActive()) {
                manager.getTransaction().rollback();
            }
            manager.close();
        }
    }

    @Test
    void persistedSchedulePreservesUtcInstantRoundTripInMySql() {
        Instant expectedStart = Instant.parse("2026-10-06T12:30:45.123456Z");
        Instant expectedEnd = Instant.parse("2026-10-06T18:00:00Z");

        EntityManager writeManager = entityManagerFactory.createEntityManager();
        Long flagId;
        try {
            writeManager.getTransaction().begin();
            FeatureFlag flag = FeatureFlag.builder()
                    .name("Scheduled checkout")
                    .flagKey("scheduled-checkout-it")
                    .environment("PROD")
                    .enabled(true)
                    .rolloutPercentage(100)
                    .startDate(expectedStart)
                    .endDate(expectedEnd)
                    .build();
            writeManager.persist(flag);
            writeManager.getTransaction().commit();
            flagId = flag.getId();
        } finally {
            if (writeManager.getTransaction().isActive()) {
                writeManager.getTransaction().rollback();
            }
            writeManager.close();
        }

        EntityManager readManager = entityManagerFactory.createEntityManager();
        try {
            readManager.getTransaction().begin();
            FeatureFlag loaded = readManager.find(FeatureFlag.class, flagId);
            assertThat(loaded).isNotNull();
            assertThat(loaded.getStartDate()).isEqualTo(expectedStart);
            assertThat(loaded.getEndDate()).isEqualTo(expectedEnd);
            readManager.getTransaction().commit();
        } finally {
            if (readManager.getTransaction().isActive()) {
                readManager.getTransaction().rollback();
            }
            readManager.close();
        }
    }

    @Test
    void legacyDateTimeValuesReadAsUtcInstantsAndUnchangedSavesPreserveRawValues() {
        EntityManager insertManager = entityManagerFactory.createEntityManager();
        Long flagId;
        try {
            insertManager.getTransaction().begin();
            insertManager.createNativeQuery(
                    "INSERT INTO feature_flags (name, flag_key, environment, enabled, rollout_percentage, description, start_date, end_date, version) " +
                    "VALUES ('Legacy Schedule', 'legacy-sched-1', 'DEV', 1, 100, 'native insert', '2026-10-06 12:30:45.123456', '2026-10-06 18:00:00.000000', 0)"
            ).executeUpdate();
            Number generatedId = (Number) insertManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult();
            flagId = generatedId.longValue();
            insertManager.getTransaction().commit();
        } finally {
            if (insertManager.getTransaction().isActive()) {
                insertManager.getTransaction().rollback();
            }
            insertManager.close();
        }

        EntityManager readManager = entityManagerFactory.createEntityManager();
        try {
            readManager.getTransaction().begin();
            FeatureFlag flag = readManager.find(FeatureFlag.class, flagId);
            assertThat(flag).isNotNull();
            assertThat(flag.getStartDate()).isEqualTo(Instant.parse("2026-10-06T12:30:45.123456Z"));
            assertThat(flag.getEndDate()).isEqualTo(Instant.parse("2026-10-06T18:00:00Z"));

            flag.setDescription("updated description without changing dates");
            readManager.getTransaction().commit();
        } finally {
            if (readManager.getTransaction().isActive()) {
                readManager.getTransaction().rollback();
            }
            readManager.close();
        }

        EntityManager verifyManager = entityManagerFactory.createEntityManager();
        try {
            Object[] rawDates = (Object[]) verifyManager.createNativeQuery(
                    "SELECT DATE_FORMAT(start_date, '%Y-%m-%d %H:%i:%s.%f'), DATE_FORMAT(end_date, '%Y-%m-%d %H:%i:%s.%f') FROM feature_flags WHERE id = :id"
            ).setParameter("id", flagId).getSingleResult();

            assertThat(rawDates[0]).isEqualTo("2026-10-06 12:30:45.123456");
            assertThat(rawDates[1]).isEqualTo("2026-10-06 18:00:00.000000");
        } finally {
            verifyManager.close();
        }
    }

    @Test
    void readsAndSavesAreConsistentUnderNonUtcJvmTimezone() {
        TimeZone originalJvmTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

            EntityManager insertManager = entityManagerFactory.createEntityManager();
            Long flagId;
            try {
                insertManager.getTransaction().begin();
                insertManager.createNativeQuery(
                        "INSERT INTO feature_flags (name, flag_key, environment, enabled, rollout_percentage, description, start_date, end_date, version) " +
                        "VALUES ('Non-UTC JVM Flag', 'non-utc-jvm-1', 'PROD', 1, 50, 'inserted in non-utc', '2026-10-06 12:30:45.123456', '2026-10-06 18:00:00.000000', 0)"
                ).executeUpdate();
                Number generatedId = (Number) insertManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult();
                flagId = generatedId.longValue();
                insertManager.getTransaction().commit();
            } finally {
                if (insertManager.getTransaction().isActive()) {
                    insertManager.getTransaction().rollback();
                }
                insertManager.close();
            }

            EntityManager readManager = entityManagerFactory.createEntityManager();
            try {
                readManager.getTransaction().begin();
                FeatureFlag flag = readManager.find(FeatureFlag.class, flagId);
                assertThat(flag).isNotNull();
                assertThat(flag.getStartDate()).isEqualTo(Instant.parse("2026-10-06T12:30:45.123456Z"));
                assertThat(flag.getEndDate()).isEqualTo(Instant.parse("2026-10-06T18:00:00Z"));

                flag.setDescription("saved under Asia/Kolkata JVM");
                readManager.getTransaction().commit();
            } finally {
                if (readManager.getTransaction().isActive()) {
                    readManager.getTransaction().rollback();
                }
                readManager.close();
            }

            EntityManager verifyManager = entityManagerFactory.createEntityManager();
            try {
                Object[] rawDates = (Object[]) verifyManager.createNativeQuery(
                        "SELECT DATE_FORMAT(start_date, '%Y-%m-%d %H:%i:%s.%f'), DATE_FORMAT(end_date, '%Y-%m-%d %H:%i:%s.%f') FROM feature_flags WHERE id = :id"
                ).setParameter("id", flagId).getSingleResult();

                assertThat(rawDates[0]).isEqualTo("2026-10-06 12:30:45.123456");
                assertThat(rawDates[1]).isEqualTo("2026-10-06 18:00:00.000000");
            } finally {
                verifyManager.close();
            }
        } finally {
            TimeZone.setDefault(originalJvmTimeZone);
        }
    }

    @Test
    void mySqlDateTime6PrecisionIsMicrosecondsNotNanoseconds() {
        Instant nanoInstant = Instant.parse("2026-10-06T12:30:45.123456789Z");

        EntityManager writeManager = entityManagerFactory.createEntityManager();
        Long flagId;
        try {
            writeManager.getTransaction().begin();
            FeatureFlag flag = FeatureFlag.builder()
                    .name("Nano Precision Test")
                    .flagKey("nano-precision-key")
                    .environment("QA")
                    .enabled(true)
                    .rolloutPercentage(100)
                    .startDate(nanoInstant)
                    .build();
            writeManager.persist(flag);
            writeManager.getTransaction().commit();
            flagId = flag.getId();
        } finally {
            if (writeManager.getTransaction().isActive()) {
                writeManager.getTransaction().rollback();
            }
            writeManager.close();
        }

        EntityManager readManager = entityManagerFactory.createEntityManager();
        try {
            FeatureFlag loaded = readManager.find(FeatureFlag.class, flagId);
            assertThat(loaded).isNotNull();
            long nanos = loaded.getStartDate().getNano();
            assertThat(nanos % 1_000).isEqualTo(0);
            assertThat(loaded.getStartDate()).isNotEqualTo(nanoInstant);
        } finally {
            readManager.close();
        }
    }

    @Test
    void explicitVersionedToggleSucceedsAndRepeatedPayloadDoesNotReverseState() {
        Long flagId = insertFlag();

        EntityManager em1 = entityManagerFactory.createEntityManager();
        try {
            em1.getTransaction().begin();
            FeatureFlag flag = em1.find(FeatureFlag.class, flagId);
            assertThat(flag.getEnabled()).isFalse();
            assertThat(flag.getVersion()).isZero();

            flag.setEnabled(true);
            em1.getTransaction().commit();
        } finally {
            if (em1.getTransaction().isActive()) em1.getTransaction().rollback();
            em1.close();
        }

        EntityManager verifier = entityManagerFactory.createEntityManager();
        try {
            FeatureFlag flag = verifier.find(FeatureFlag.class, flagId);
            assertThat(flag.getEnabled()).isTrue();
            assertThat(flag.getVersion()).isEqualTo(1L);
        } finally {
            verifier.close();
        }

        EntityManager em2 = entityManagerFactory.createEntityManager();
        try {
            em2.getTransaction().begin();
            FeatureFlag flag = em2.find(FeatureFlag.class, flagId);
            Long staleExpectedVersion = 0L;
            assertThat(flag.getVersion()).isNotEqualTo(staleExpectedVersion);
            em2.getTransaction().rollback();
        } finally {
            em2.close();
        }

        EntityManager em3 = entityManagerFactory.createEntityManager();
        try {
            em3.getTransaction().begin();
            FeatureFlag flag = em3.find(FeatureFlag.class, flagId);
            assertThat(flag.getVersion()).isEqualTo(1L);
            flag.setEnabled(false);
            em3.getTransaction().commit();
        } finally {
            if (em3.getTransaction().isActive()) em3.getTransaction().rollback();
            em3.close();
        }

        EntityManager verifier2 = entityManagerFactory.createEntityManager();
        try {
            FeatureFlag flag = verifier2.find(FeatureFlag.class, flagId);
            assertThat(flag.getEnabled()).isFalse();
            assertThat(flag.getVersion()).isEqualTo(2L);
        } finally {
            verifier2.close();
        }
    }

    @Test
    void matchingStateWithCurrentVersionIsNoOpWithoutVersionIncrement() {
        Long flagId = insertFlag();

        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            FeatureFlag flag = em.find(FeatureFlag.class, flagId);
            assertThat(flag.getVersion()).isZero();
            assertThat(flag.getEnabled()).isFalse();
            em.getTransaction().commit();
        } finally {
            em.close();
        }

        EntityManager verifier = entityManagerFactory.createEntityManager();
        try {
            FeatureFlag flag = verifier.find(FeatureFlag.class, flagId);
            assertThat(flag.getVersion()).isZero();
            assertThat(flag.getEnabled()).isFalse();
        } finally {
            verifier.close();
        }
    }

    @Test
    void concurrentConflictingToggleWritesPermitOneWinnerAndRejectStaleWrite() {
        Long flagId = insertFlag();

        EntityManager emA = entityManagerFactory.createEntityManager();
        EntityManager emB = entityManagerFactory.createEntityManager();
        try {
            emA.getTransaction().begin();
            emB.getTransaction().begin();

            FeatureFlag flagA = emA.find(FeatureFlag.class, flagId);
            FeatureFlag flagB = emB.find(FeatureFlag.class, flagId);

            assertThat(flagA.getVersion()).isZero();
            assertThat(flagB.getVersion()).isZero();

            flagA.setEnabled(true);
            emA.getTransaction().commit();

            flagB.setEnabled(true);
            assertThrows(RollbackException.class, () -> emB.getTransaction().commit());
        } finally {
            if (emA.getTransaction().isActive()) emA.getTransaction().rollback();
            if (emB.getTransaction().isActive()) emB.getTransaction().rollback();
            emA.close();
            emB.close();
        }

        EntityManager verifier = entityManagerFactory.createEntityManager();
        try {
            FeatureFlag persisted = verifier.find(FeatureFlag.class, flagId);
            assertThat(persisted.getEnabled()).isTrue();
            assertThat(persisted.getVersion()).isEqualTo(1L);
        } finally {
            verifier.close();
        }
    }

    @Test
    void sameIdentityUpdateSucceedsAndVersionIncrements() {
        Long flagId = insertFlag();

        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            FeatureFlag flag = em.find(FeatureFlag.class, flagId);
            String originalKey = flag.getFlagKey();
            String originalEnv = flag.getEnvironment();

            flag.setName("Updated Display Name");
            flag.setDescription("Updated description");
            flag.setRolloutPercentage(80);
            em.getTransaction().commit();

            assertThat(flag.getFlagKey()).isEqualTo(originalKey);
            assertThat(flag.getEnvironment()).isEqualTo(originalEnv);
            assertThat(flag.getVersion()).isEqualTo(1L);
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }

        EntityManager verifier = entityManagerFactory.createEntityManager();
        try {
            FeatureFlag persisted = verifier.find(FeatureFlag.class, flagId);
            assertThat(persisted.getName()).isEqualTo("Updated Display Name");
            assertThat(persisted.getVersion()).isEqualTo(1L);
        } finally {
            verifier.close();
        }
    }

    @Test
    void duplicateKeyAndEnvironmentOnInsertViolatesConstraint() {
        String key = "unique-key-" + UUID.randomUUID();
        Long firstId = insertFlag(key);
        assertThat(firstId).isNotNull();

        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            FeatureFlag duplicate = FeatureFlag.builder()
                    .name("Duplicate Flag")
                    .flagKey(key)
                    .environment("DEV")
                    .enabled(false)
                    .rolloutPercentage(0)
                    .build();
            assertThrows(
                    jakarta.persistence.PersistenceException.class,
                    () -> em.persist(duplicate)
            );
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
    }

    @Test
    void committedMutationIsImmediatelyVisibleToAuthoritativeRead() {
        String key = "freshness-key-" + UUID.randomUUID();
        Long flagId = insertFlag(key);

        EntityManager mutationManager = entityManagerFactory.createEntityManager();
        try {
            mutationManager.getTransaction().begin();
            FeatureFlag flag = mutationManager.find(FeatureFlag.class, flagId);
            flag.setEnabled(true);
            flag.setRolloutPercentage(100);
            mutationManager.getTransaction().commit();
        } finally {
            if (mutationManager.getTransaction().isActive()) mutationManager.getTransaction().rollback();
            mutationManager.close();
        }

        EntityManager queryManager = entityManagerFactory.createEntityManager();
        try {
            queryManager.getTransaction().begin();
            FeatureFlag fresh = queryManager.createQuery(
                    "SELECT f FROM FeatureFlag f WHERE f.flagKey = :flagKey AND f.environment = :environment",
                    FeatureFlag.class)
                    .setParameter("flagKey", key)
                    .setParameter("environment", "DEV")
                    .getSingleResult();
            assertThat(fresh.getEnabled()).isTrue();
            assertThat(fresh.getRolloutPercentage()).isEqualTo(100);
            assertThat(fresh.getVersion()).isEqualTo(1L);
            queryManager.getTransaction().commit();
        } finally {
            if (queryManager.getTransaction().isActive()) queryManager.getTransaction().rollback();
            queryManager.close();
        }
    }
}
