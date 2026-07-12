package com.thiago.aidata.dto;

import java.util.List;

public record TurnResponseInput(
    String provider,
    String responseText,
    int tokensIn,
    int tokensOut,
    boolean cacheHit,
    boolean ragUsed,
    List<String> toolsUsed,
    Long durationMs
) {}
