package com.featureflag.flag_service.event;

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
    private String timestamp;
}
