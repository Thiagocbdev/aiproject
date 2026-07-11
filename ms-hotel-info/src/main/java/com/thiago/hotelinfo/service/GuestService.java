package com.thiago.hotelinfo.service;

import com.thiago.hotelinfo.dto.GuestInput;
import com.thiago.hotelinfo.dto.GuestResponse;
import com.thiago.hotelinfo.exception.NotFoundException;
import com.thiago.hotelinfo.model.Guest;
import com.thiago.hotelinfo.repository.GuestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestService {
    private final GuestRepository guestRepository;

    public List<GuestResponse> listGuests(String search) {
        List<Guest> guests = (search != null && !search.isBlank())
            ? guestRepository.findByNameOrEmailContaining(search)
            : guestRepository.findAll();
        return guests.stream().map(this::toResponse).toList();
    }

    public GuestResponse getGuest(UUID id) {
        return guestRepository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new NotFoundException("Guest not found: " + id));
    }

    public GuestResponse createGuest(GuestInput input) {
        Guest guest = Guest.builder()
            .name(input.name())
            .email(input.email())
            .phone(input.phone())
            .checkIn(input.checkIn())
            .checkOut(input.checkOut())
            .build();
        return toResponse(guestRepository.save(guest));
    }

    private GuestResponse toResponse(Guest g) {
        return new GuestResponse(
            g.getId(),
            g.getName(),
            g.getEmail(),
            g.getPhone(),
            g.getCheckIn(),
            g.getCheckOut(),
            g.getRoomNumber(),
            g.getLoyaltyTier() != null ? g.getLoyaltyTier().name() : null
        );
    }
}
