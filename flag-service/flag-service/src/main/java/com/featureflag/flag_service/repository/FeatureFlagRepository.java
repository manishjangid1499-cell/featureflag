package com.featureflag.flag_service.repository;

import com.featureflag.flag_service.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {

    Optional<FeatureFlag> findByFlagKey(String flagKey);

    Optional<FeatureFlag> findByFlagKeyAndEnvironment(
            String flagKey,
            String environment
    );

    List<FeatureFlag> findByEnvironment(String environment);
}