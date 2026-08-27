package com.featureflag.flag_service.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    private String sourceService;
    private String actor;
    private FlagAuditSnapshot before;
    private FlagAuditSnapshot after;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime occurredAt;

    public FlagEvent(
            String eventId,
            String eventType,
            String flagKey,
            String environment,
            String timestamp
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.flagKey = flagKey;
        this.environment = environment;
        this.timestamp = timestamp;
    }
}
