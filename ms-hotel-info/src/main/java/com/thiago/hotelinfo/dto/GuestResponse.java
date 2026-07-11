package com.thiago.hotelinfo.dto;

import java.time.LocalDate;
import java.util.UUID;

public record GuestResponse(
    UUID id,
    String name,
    String email,
    String phone,
    LocalDate checkIn,
    LocalDate checkOut,
    String roomNumber,
    String loyaltyTier
) {}
