package com.thiago.aidata.dto;

import jakarta.validation.constraints.NotBlank;

public record VectorSearchRequest(
    @NotBlank String query,
    Integer topK
) {}
