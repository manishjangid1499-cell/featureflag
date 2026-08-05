package com.featureflag.flag_service.service;

import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FlagService {

    private final FeatureFlagRepository repository;

    public FeatureFlag createFlag(FlagRequest request) {

        FeatureFlag flag = FeatureFlag.builder()
                .name(request.getName())
                .flagKey(request.getFlagKey())
                .enabled(request.getEnabled())
                .description(request.getDescription())
                .build();

        return repository.save(flag);
    }

    public List<FeatureFlag> getAllFlags() {
        return repository.findAll();
    }

    public FeatureFlag getByKey(String key) {

        return repository.findByFlagKey(key)
                .orElseThrow(() ->
                        new RuntimeException("Flag not found"));
    }


    public FeatureFlag updateFlag(Long id, FlagRequest request) {

        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Flag not found"));

        flag.setName(request.getName());
        flag.setFlagKey(request.getFlagKey());
        flag.setEnabled(request.getEnabled());
        flag.setDescription(request.getDescription());

        return repository.save(flag);
    }

    public String deleteFlag(Long id) {

        repository.deleteById(id);

        return "Flag Deleted Successfully";
    }

    public FeatureFlag toggleFlag(Long id) {

        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Flag not found"));

        flag.setEnabled(!flag.getEnabled());

        return repository.save(flag);
    }
}