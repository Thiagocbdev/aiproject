package com.thiago.hotelinfo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record BookingInput(
    @NotBlank String guestId,
    @NotBlank String serviceType,
    @NotNull LocalDate date,
    @NotNull LocalTime time
) {}
