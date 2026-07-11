package com.thiago.hotelconcierge.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AskRequest(
    @NotBlank String message,
    String sessionId,
    List<String> providers
) {}
