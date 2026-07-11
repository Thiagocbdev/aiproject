package com.thiago.aidata.dto;

import java.time.LocalDateTime;

public record CacheEntry(
    String key,
    String response,
    String provider,
    LocalDateTime createdAt,
    Long ttlSeconds
) {}
