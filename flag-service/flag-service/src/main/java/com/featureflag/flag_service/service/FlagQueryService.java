package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlagQueryService {

    private final FeatureFlagRepository featureFlagRepository;

    @Transactional(readOnly = true)
    public Page<FeatureFlag> findAll(Pageable pageable) {
        return featureFlagRepository.findAll(pageable);
    }
}
