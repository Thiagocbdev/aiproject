package com.thiago.hotelinfo.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(
    String serviceType,
    LocalDate date,
    boolean available,
    List<String> availableSlots
) {}
