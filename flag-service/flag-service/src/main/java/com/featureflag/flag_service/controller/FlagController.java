package com.featureflag.flag_service.controller;

import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.service.FlagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/flags")
@RequiredArgsConstructor
public class FlagController {

    private final FlagService flagService;

    @PostMapping
    public FeatureFlag createFlag(
            @RequestBody FlagRequest request) {

        return flagService.createFlag(request);
    }

    @GetMapping
    public List<FeatureFlag> getAllFlags() {

        return flagService.getAllFlags();
    }

    @GetMapping("/{key}")
    public FeatureFlag getFlagByKey(
            @PathVariable String key) {

        return flagService.getByKey(key);
    }
}