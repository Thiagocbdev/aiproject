package com.thiago.hotelinfo.controller;

import com.thiago.hotelinfo.dto.BookingInput;
import com.thiago.hotelinfo.dto.BookingPatch;
import com.thiago.hotelinfo.dto.BookingResponse;
import com.thiago.hotelinfo.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;

    @GetMapping
    public List<BookingResponse> list(@RequestParam(required = false) String guestId) {
        return bookingService.listBookings(guestId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody BookingInput input) {
        return bookingService.createBooking(input);
    }

    @GetMapping("/{bookingId}")
    public BookingResponse get(@PathVariable UUID bookingId) {
        return bookingService.getBooking(bookingId);
    }

    @PatchMapping("/{bookingId}")
    public BookingResponse update(@PathVariable UUID bookingId, @RequestBody BookingPatch patch) {
        return bookingService.updateBooking(bookingId, patch);
    }
}
