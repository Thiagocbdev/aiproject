package com.thiago.aidata.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record TrainingExampleInput(
    @NotBlank String provider,
    @NotBlank String message,
    @NotBlank String response,
    List<String> toolsUsed,
    Integer rating
) {}
