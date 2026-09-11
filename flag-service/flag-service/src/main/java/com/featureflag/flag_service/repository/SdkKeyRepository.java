package com.featureflag.flag_service.repository;

import com.featureflag.flag_service.entity.SdkKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface SdkKeyRepository extends JpaRepository<SdkKey, Long> {

    Optional<SdkKey> findByKeyHashAndActiveTrue(String keyHash);

    Page<SdkKey> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
