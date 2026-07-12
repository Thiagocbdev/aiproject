package com.thiago.aidata.dto;

public record CacheSavingsDto(
    long cacheHitCount,
    long actualTokensSpent,
    long estimatedSavedTokens
) {}
