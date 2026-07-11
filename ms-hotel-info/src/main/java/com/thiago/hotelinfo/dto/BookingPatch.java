package com.thiago.hotelinfo.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record BookingPatch(
    String status,
    LocalDate date,
    LocalTime time
) {}
