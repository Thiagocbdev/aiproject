package com.thiago.aidata.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record VectorIndexRequest(
    @NotBlank String id,
    @NotBlank String content,
    Map<String, String> metadata
) {}
