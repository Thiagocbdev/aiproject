package com.thiago.aidata.dto;

public record CacheEntryInput(
    String response,
    String provider,
    Integer ttlSeconds
) {}
