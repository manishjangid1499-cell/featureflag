package com.featureflag.flag_service.repository;

import com.featureflag.flag_service.entity.SdkKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SdkKeyRepository extends JpaRepository<SdkKey, Long> {

    Optional<SdkKey> findByKeyHashAndActiveTrue(String keyHash);

    List<SdkKey> findAllByOrderByCreatedAtDescIdDesc();
}
