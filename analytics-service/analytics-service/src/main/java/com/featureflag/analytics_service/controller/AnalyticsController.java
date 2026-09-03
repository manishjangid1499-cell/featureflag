package com.featureflag.analytics_service.controller;

import com.featureflag.analytics_service.dto.AnalyticsResponse;
import com.featureflag.analytics_service.dto.PageResponse;
import com.featureflag.analytics_service.service.AnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Get all analytics.
     */
    @GetMapping
    public PageResponse<AnalyticsResponse> getAllAnalytics(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                analyticsService.getAllAnalytics(
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.DESC, "id")
                        )
                ),
                AnalyticsResponse::from
        );
    }

    /**
     * Get analytics for a specific feature flag.
     */
    @GetMapping("/{flagKey}")
    public PageResponse<AnalyticsResponse> getAnalyticsByFlagKey(
            @PathVariable String flagKey,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                analyticsService.getAnalyticsByFlagKey(
                        flagKey,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.DESC, "id")
                        )
                ),
                AnalyticsResponse::from
        );
    }

    /**
     * Get analytics by database ID.
     */
    @GetMapping("/id/{id}")
    public AnalyticsResponse getAnalyticsById(
            @PathVariable Long id
    ) {

        return AnalyticsResponse.from(
                analyticsService.getAnalyticsById(id)
        );
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
