package com.featureflag.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    private String eventId;
    private String recipient;
    private String creatorEmail;
    private String subject;
    private String message;
    private String type;

    private List<String> recipients;

    public NotificationEvent(
            String eventId,
            String recipient,
            String creatorEmail,
            String subject,
            String message,
            String type
    ) {
        this(
                eventId,
                recipient,
                creatorEmail,
                subject,
                message,
                type,
                null
        );
    }
}
