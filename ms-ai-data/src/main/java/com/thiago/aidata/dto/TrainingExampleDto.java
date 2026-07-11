package com.thiago.aidata.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TrainingExampleDto(
    UUID id,
    String provider,
    String message,
    String response,
    List<String> toolsUsed,
    Integer rating,
    LocalDateTime createdAt
) {}
