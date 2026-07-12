package com.thiago.aidata.dto;

import java.time.LocalDate;

public record AnalyticsTokenDto(
    String provider,
    LocalDate day,
    long tokensIn,
    long tokensOut,
    long calls,
    long cacheHits
) {}
