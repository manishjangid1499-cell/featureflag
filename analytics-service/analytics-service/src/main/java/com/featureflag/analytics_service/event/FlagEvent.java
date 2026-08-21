package com.featureflag.analytics_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlagEvent {

    private String eventId;
    private String eventType;
    private String flagKey;
    private String environment;
    private String timestamp;
}
