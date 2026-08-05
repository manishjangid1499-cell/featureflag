package com.featureflag.flag_service.dto;

import lombok.Data;

@Data
public class FlagRequest {

    private String name;
    private String flagKey;
    private Boolean enabled;
    private String description;
}