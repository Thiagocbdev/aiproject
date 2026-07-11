package com.thiago.hotelconcierge.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name = "hotel-info", url = "${concierge.hotel-info-url}")
public interface HotelInfoClient {

    @GetMapping("/api/v1/rooms/{serviceType}/availability")
    Map<String, Object> getAvailability(
        @PathVariable("serviceType") String serviceType,
        @RequestParam("date") String date,
        @RequestParam(value = "time", required = false) String time
    );

    @GetMapping("/api/v1/pricing")
    Map<String, Object> getPrice(
        @RequestParam("roomType") String roomType,
        @RequestParam("date") String date
    );

    @PostMapping("/api/v1/bookings")
    Map<String, Object> createBooking(@RequestBody Map<String, Object> bookingRequest);

    @GetMapping("/api/v1/guests/{guestId}")
    Map<String, Object> getGuest(@PathVariable("guestId") String guestId);
}
