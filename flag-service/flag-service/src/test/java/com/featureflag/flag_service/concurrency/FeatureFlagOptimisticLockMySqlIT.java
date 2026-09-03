package com.featureflag.flag_service.concurrency;

import com.featureflag.flag_service.entity.FeatureFlag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
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
        EntityManager manager =
                entityManagerFactory.createEntityManager();
        try {
            manager.getTransaction().begin();
            FeatureFlag flag = FeatureFlag.builder()
                    .name("Concurrent checkout")
                    .flagKey("concurrent-checkout")
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
}
