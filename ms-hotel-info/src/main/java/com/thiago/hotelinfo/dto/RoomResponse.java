package com.thiago.hotelinfo.dto;

import java.math.BigDecimal;

public record RoomResponse(
    String number,
    String type,
    Integer floor,
    Integer capacity,
    BigDecimal pricePerNight,
    String status
) {}
