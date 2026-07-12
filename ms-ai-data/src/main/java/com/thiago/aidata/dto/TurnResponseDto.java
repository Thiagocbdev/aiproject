package com.thiago.aidata.dto;

import java.util.List;

public record TurnResponseDto(
    Long id,
    String provider,
    String responseText,
    int tokensIn,
    int tokensOut,
    boolean cacheHit,
    boolean ragUsed,
    List<String> toolsUsed,
    Long durationMs
) {}
