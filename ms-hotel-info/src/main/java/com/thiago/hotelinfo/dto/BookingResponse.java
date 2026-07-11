package com.thiago.hotelinfo.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingResponse(
    UUID id,
    String guestId,
    String serviceType,
    LocalDate date,
    LocalTime time,
    String status
) {}
