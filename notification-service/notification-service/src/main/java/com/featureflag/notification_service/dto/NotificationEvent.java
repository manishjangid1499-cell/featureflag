package com.featureflag.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
