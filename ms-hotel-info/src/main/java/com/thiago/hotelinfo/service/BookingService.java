package com.thiago.hotelinfo.service;

import com.thiago.hotelinfo.dto.BookingInput;
import com.thiago.hotelinfo.dto.BookingPatch;
import com.thiago.hotelinfo.dto.BookingResponse;
import com.thiago.hotelinfo.exception.BookingConflictException;
import com.thiago.hotelinfo.exception.NotFoundException;
import com.thiago.hotelinfo.model.Guest;
import com.thiago.hotelinfo.model.ServiceBooking;
import com.thiago.hotelinfo.model.ServiceType;
import com.thiago.hotelinfo.repository.GuestRepository;
import com.thiago.hotelinfo.repository.ServiceBookingRepository;
import com.thiago.hotelinfo.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final ServiceBookingRepository serviceBookingRepository;
    private final GuestRepository guestRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    public List<BookingResponse> listBookings(String guestId) {
        List<ServiceBooking> bookings;
        if (guestId != null && !guestId.isBlank()) {
            UUID guestUuid = UUID.fromString(guestId);
            bookings = serviceBookingRepository.findByGuestId(guestUuid);
        } else {
            bookings = serviceBookingRepository.findAll();
        }
        return bookings.stream().map(this::toResponse).toList();
    }

    public BookingResponse getBooking(UUID id) {
        return serviceBookingRepository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new NotFoundException("Booking not found: " + id));
    }

    @Transactional
    public BookingResponse createBooking(BookingInput input) {
        UUID guestUuid = UUID.fromString(input.guestId());
        Guest guest = guestRepository.findById(guestUuid)
            .orElseThrow(() -> new NotFoundException("Guest not found: " + input.guestId()));

        ServiceType serviceType = serviceTypeRepository.findByNameIgnoreCase(input.serviceType())
            .orElseThrow(() -> new NotFoundException("Service type not found: " + input.serviceType()));

        LocalDateTime scheduledAt = LocalDateTime.of(input.date(), input.time());

        // Check for conflicts (only if the service has slot-based scheduling)
        if (serviceType.getSlotDurationMinutes() != null) {
            LocalDateTime slotEnd = scheduledAt.plusMinutes(serviceType.getSlotDurationMinutes() - 1);
            List<ServiceBooking> conflicts = serviceBookingRepository.findConflicting(
                serviceType.getId(), scheduledAt, slotEnd
            );
            if (!conflicts.isEmpty()) {
                throw new BookingConflictException(
                    "Time slot " + input.time() + " on " + input.date() + " is already booked for " + serviceType.getName()
                );
            }
        }

        ServiceBooking booking = ServiceBooking.builder()
            .guest(guest)
            .serviceType(serviceType)
            .scheduledAt(scheduledAt)
            .durationMinutes(serviceType.getSlotDurationMinutes())
            .status(ServiceBooking.BookingStatus.CONFIRMED)
            .build();

        return toResponse(serviceBookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse updateBooking(UUID id, BookingPatch patch) {
        ServiceBooking booking = serviceBookingRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Booking not found: " + id));

        if (patch.status() != null) {
            try {
                booking.setStatus(ServiceBooking.BookingStatus.valueOf(patch.status().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + patch.status());
            }
        }

        if (patch.date() != null || patch.time() != null) {
            LocalDateTime currentScheduledAt = booking.getScheduledAt();
            LocalDate newDate = patch.date() != null ? patch.date() : currentScheduledAt.toLocalDate();
            LocalTime newTime = patch.time() != null ? patch.time() : currentScheduledAt.toLocalTime();
            booking.setScheduledAt(LocalDateTime.of(newDate, newTime));
        }

        return toResponse(serviceBookingRepository.save(booking));
    }

    private BookingResponse toResponse(ServiceBooking b) {
        return new BookingResponse(
            b.getId(),
            b.getGuest().getId().toString(),
            b.getServiceType().getName(),
            b.getScheduledAt().toLocalDate(),
            b.getScheduledAt().toLocalTime(),
            b.getStatus() != null ? b.getStatus().name() : null
        );
    }
}
