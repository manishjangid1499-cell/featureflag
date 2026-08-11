package com.featureflag.analytics_service.controller;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Get all analytics.
     */
    @GetMapping
    public List<AnalyticsEvent> getAllAnalytics() {

        return analyticsService.getAllAnalytics();
    }

    /**
     * Get analytics for a specific feature flag.
     */
    @GetMapping("/{flagKey}")
    public List<AnalyticsEvent> getAnalyticsByFlagKey(
            @PathVariable String flagKey
    ) {

        return analyticsService.getAnalyticsByFlagKey(flagKey);
    }

    /**
     * Get analytics by database ID.
     */
    @GetMapping("/id/{id}")
    public AnalyticsEvent getAnalyticsById(
            @PathVariable Long id
    ) {

        return analyticsService.getAnalyticsById(id);
    }

    /**
     * Delete analytics record.
     */
    @DeleteMapping("/{id}")
    public String deleteAnalytics(
            @PathVariable Long id
    ) {

        analyticsService.deleteAnalytics(id);

        return "Analytics record deleted successfully";
    }
}