package com.thiago.hotelinfo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceResponse(
    String serviceType,
    LocalDate date,
    BigDecimal amount,
    String currency
) {}
