package com.thiago.hotelconcierge.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "hotel-info", url = "${concierge.hotel-info-url}")
public interface HotelInfoClient {

    // ── Guests ──────────────────────────────────────────────────────

    @GetMapping("/api/v1/guests")
    List<Map<String, Object>> searchGuests(@RequestParam(value = "search", required = false) String search);

    @GetMapping("/api/v1/guests/{guestId}")
    Map<String, Object> getGuest(@PathVariable("guestId") String guestId);

    @PostMapping("/api/v1/guests")
    Map<String, Object> createGuest(@RequestBody Map<String, Object> guestInput);

    // ── Rooms ───────────────────────────────────────────────────────

    @GetMapping("/api/v1/rooms")
    List<Map<String, Object>> listRooms(@RequestParam(value = "type", required = false) String type);

    @GetMapping("/api/v1/rooms/{serviceType}/availability")
    Map<String, Object> getAvailability(
        @PathVariable("serviceType") String serviceType,
        @RequestParam("date") String date,
        @RequestParam(value = "time", required = false) String time
    );

    // ── Pricing ─────────────────────────────────────────────────────

    @GetMapping("/api/v1/pricing")
    Map<String, Object> getPrice(
        @RequestParam("serviceType") String serviceType,
        @RequestParam("date") String date
    );

    // ── Bookings ────────────────────────────────────────────────────

    @GetMapping("/api/v1/bookings")
    List<Map<String, Object>> listBookings(@RequestParam(value = "guestId", required = false) String guestId);

    @PostMapping("/api/v1/bookings")
    Map<String, Object> createBooking(@RequestBody Map<String, Object> bookingRequest);

    @GetMapping("/api/v1/bookings/{bookingId}")
    Map<String, Object> getBooking(@PathVariable("bookingId") String bookingId);

    @PatchMapping("/api/v1/bookings/{bookingId}")
    Map<String, Object> updateBooking(
        @PathVariable("bookingId") String bookingId,
        @RequestBody Map<String, Object> patch
    );
}
