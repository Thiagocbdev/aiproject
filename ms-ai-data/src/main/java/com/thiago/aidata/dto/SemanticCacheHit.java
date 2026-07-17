package com.thiago.aidata.dto;

public record SemanticCacheHit(
    String response,
    Double score,
    boolean semantic
) {}
