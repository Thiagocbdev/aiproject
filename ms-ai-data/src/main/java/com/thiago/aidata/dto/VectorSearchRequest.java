package com.thiago.aidata.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record VectorSearchRequest(
    @NotBlank String query,
    Integer topK,
    Double similarityThreshold,
    List<String> categories
) {}
