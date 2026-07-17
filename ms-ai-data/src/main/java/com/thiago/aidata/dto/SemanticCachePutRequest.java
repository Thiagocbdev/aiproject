package com.thiago.aidata.dto;

public record SemanticCachePutRequest(
    String provider,
    String message,
    String response,
    Integer ttlSeconds
) {}
