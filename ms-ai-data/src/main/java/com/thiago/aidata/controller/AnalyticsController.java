package com.thiago.aidata.controller;

import com.thiago.aidata.dto.AnalyticsTokenDto;
import com.thiago.aidata.dto.CacheSavingsDto;
import com.thiago.aidata.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/tokens")
    public List<AnalyticsTokenDto> getTokens(
        @RequestParam(defaultValue = "30") int days
    ) {
        return analyticsService.getTokenAnalytics(days);
    }

    @GetMapping("/cache-savings")
    public CacheSavingsDto getCacheSavings() {
        return analyticsService.getCacheSavings();
    }
}
