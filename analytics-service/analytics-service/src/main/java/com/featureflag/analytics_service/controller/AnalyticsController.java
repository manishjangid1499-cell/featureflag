package com.featureflag.analytics_service.controller;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.repository.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsEventRepository analyticsEventRepository;

    @GetMapping
    public List<AnalyticsEvent> getAllAnalytics() {
        return analyticsEventRepository.findAll();
    }

    @GetMapping("/{flagKey}")
    public List<AnalyticsEvent> getAnalyticsByFlagKey(
            @PathVariable String flagKey
    ) {
        return analyticsEventRepository
                .findAll()
                .stream()
                .filter(event ->
                        event.getFlagKey().equalsIgnoreCase(flagKey)
                )
                .collect(Collectors.toList());
    }
}