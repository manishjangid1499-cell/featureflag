package com.featureflag.analytics_service.repository;

import com.featureflag.analytics_service.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, String> {
}
