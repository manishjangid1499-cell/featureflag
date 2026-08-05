package com.featureflag.auth_service.dto;

import lombok.Data;

@Data
public class FeatureFlagResponse {

    private Long id;
    private String name;
    private String flagKey;
    private Boolean enabled;
    private String description;
}