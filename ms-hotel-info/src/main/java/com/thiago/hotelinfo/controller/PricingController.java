package com.thiago.hotelinfo.controller;

import com.thiago.hotelinfo.dto.PriceResponse;
import com.thiago.hotelinfo.exception.NotFoundException;
import com.thiago.hotelinfo.model.Room;
import com.thiago.hotelinfo.model.ServiceType;
import com.thiago.hotelinfo.repository.RoomRepository;
import com.thiago.hotelinfo.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {
    private final ServiceTypeRepository serviceTypeRepository;
    private final RoomRepository roomRepository;

    @GetMapping
    public PriceResponse getPrice(
        @RequestParam String serviceType,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate priceDate = (date != null) ? date : LocalDate.now();

        // Try service types first (spa, restaurant, gym, room_service)
        var serviceTypeOpt = serviceTypeRepository.findByNameIgnoreCase(serviceType);
        if (serviceTypeOpt.isPresent()) {
            ServiceType st = serviceTypeOpt.get();
            return new PriceResponse(st.getName(), priceDate, st.getPricePerSlot(), "BRL");
        }

        // Fallback: try room types (standard, deluxe, suite) — return price_per_night
        try {
            Room.RoomType roomType = Room.RoomType.valueOf(serviceType.toUpperCase());
            List<Room> rooms = roomRepository.findByType(roomType);
            if (!rooms.isEmpty()) {
                var avgPrice = rooms.stream()
                    .map(Room::getPricePerNight)
                    .filter(p -> p != null)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                    .divide(java.math.BigDecimal.valueOf(rooms.size()), 2, java.math.RoundingMode.HALF_UP);
                return new PriceResponse(serviceType.toLowerCase(), priceDate, avgPrice, "BRL");
            }
        } catch (IllegalArgumentException ignored) {
            // Not a room type either
        }

        throw new NotFoundException("Service or room type not found: " + serviceType);
    }
}
