package com.featureflag.notification_service.repository;

import com.featureflag.notification_service.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, String> {
}
