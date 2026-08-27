package com.featureflag.flag_service.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlagEvent {

    public static final String EVALUATION_ENABLED =
            "EVALUATION_ENABLED";

    public static final String EVALUATION_DISABLED =
            "EVALUATION_DISABLED";

    private String eventId;
    private String eventType;
    private String flagKey;
    private String environment;
    private String timestamp;
}
