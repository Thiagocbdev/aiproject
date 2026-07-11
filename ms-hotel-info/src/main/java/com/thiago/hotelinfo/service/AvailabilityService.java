package com.thiago.hotelinfo.service;

import com.thiago.hotelinfo.dto.AvailabilityResponse;
import com.thiago.hotelinfo.exception.NotFoundException;
import com.thiago.hotelinfo.model.ServiceBooking;
import com.thiago.hotelinfo.model.ServiceType;
import com.thiago.hotelinfo.repository.ServiceBookingRepository;
import com.thiago.hotelinfo.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityService {
    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceBookingRepository serviceBookingRepository;

    private static final DateTimeFormatter SLOT_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public AvailabilityResponse checkAvailability(String serviceTypeName, LocalDate date, LocalTime requestedTime) {
        ServiceType serviceType = serviceTypeRepository.findByNameIgnoreCase(serviceTypeName)
            .orElseThrow(() -> new NotFoundException("Service type not found: " + serviceTypeName));

        LocalDate checkDate = (date != null) ? date : LocalDate.now();

        // If no slotDuration defined (e.g. gym, room_service), always available
        if (serviceType.getSlotDurationMinutes() == null) {
            return new AvailabilityResponse(
                serviceType.getName(),
                checkDate,
                true,
                List.of()
            );
        }

        // Generate all possible slots for the day
        List<String> allSlots = generateSlots(serviceType.getOpenTime(), serviceType.getCloseTime(), serviceType.getSlotDurationMinutes());

        // Get already booked slots for the day
        LocalDateTime startOfDay = checkDate.atStartOfDay();
        LocalDateTime endOfDay = checkDate.atTime(23, 59, 59);
        List<ServiceBooking> existingBookings = serviceBookingRepository.findConflicting(
            serviceType.getId(), startOfDay, endOfDay
        );

        Set<String> bookedSlots = existingBookings.stream()
            .map(b -> b.getScheduledAt().toLocalTime().format(SLOT_FORMATTER))
            .collect(Collectors.toSet());

        List<String> freeSlots = allSlots.stream()
            .filter(slot -> !bookedSlots.contains(slot))
            .toList();

        // If a specific time was requested, check that slot specifically
        if (requestedTime != null) {
            String requestedSlot = requestedTime.format(SLOT_FORMATTER);
            boolean slotAvailable = freeSlots.contains(requestedSlot);
            return new AvailabilityResponse(
                serviceType.getName(),
                checkDate,
                slotAvailable,
                slotAvailable ? List.of(requestedSlot) : List.of()
            );
        }

        return new AvailabilityResponse(
            serviceType.getName(),
            checkDate,
            !freeSlots.isEmpty(),
            freeSlots
        );
    }

    private List<String> generateSlots(LocalTime openTime, LocalTime closeTime, int slotDurationMinutes) {
        List<String> slots = new ArrayList<>();
        if (openTime == null || closeTime == null) {
            return slots;
        }
        LocalTime current = openTime;
        while (!current.plusMinutes(slotDurationMinutes).isAfter(closeTime)) {
            slots.add(current.format(SLOT_FORMATTER));
            current = current.plusMinutes(slotDurationMinutes);
        }
        return slots;
    }
}
