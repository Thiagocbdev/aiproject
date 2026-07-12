package com.thiago.aidata.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TurnDto(
    Long id,
    String sessionId,
    int turnNumber,
    String question,
    boolean useContext,
    List<TurnResponseDto> responses,
    LocalDateTime createdAt
) {}
