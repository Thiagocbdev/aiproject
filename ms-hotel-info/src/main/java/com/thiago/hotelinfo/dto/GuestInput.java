package com.thiago.hotelinfo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record GuestInput(
    @NotBlank String name,
    @NotBlank @Email String email,
    String phone,
    LocalDate checkIn,
    LocalDate checkOut
) {}
