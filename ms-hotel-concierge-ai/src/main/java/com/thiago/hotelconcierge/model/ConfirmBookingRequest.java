package com.thiago.hotelconcierge.model;

import jakarta.validation.constraints.NotBlank;

public record ConfirmBookingRequest(@NotBlank String pendingActionId, boolean confirm) {}
