package com.featureflag.audit_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlagEvent {

    private String eventType;
    private String flagKey;
    private String timestamp;
}