package com.thiago.hotelinfo.controller;

import com.thiago.hotelinfo.dto.AvailabilityResponse;
import com.thiago.hotelinfo.dto.RoomResponse;
import com.thiago.hotelinfo.service.AvailabilityService;
import com.thiago.hotelinfo.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;
    private final AvailabilityService availabilityService;

    @GetMapping
    public List<RoomResponse> listRooms(@RequestParam(required = false) String type) {
        return roomService.listRooms(type);
    }

    @GetMapping("/{serviceType}/availability")
    public AvailabilityResponse checkAvailability(
        @PathVariable String serviceType,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time
    ) {
        return availabilityService.checkAvailability(serviceType, date, time);
    }
}
