package com.thiago.hotelinfo.controller;

import com.thiago.hotelinfo.dto.GuestInput;
import com.thiago.hotelinfo.dto.GuestResponse;
import com.thiago.hotelinfo.service.GuestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/guests")
@RequiredArgsConstructor
public class GuestController {
    private final GuestService guestService;

    @GetMapping
    public List<GuestResponse> list(@RequestParam(required = false) String search) {
        return guestService.listGuests(search);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuestResponse create(@Valid @RequestBody GuestInput input) {
        return guestService.createGuest(input);
    }

    @GetMapping("/{guestId}")
    public GuestResponse get(@PathVariable UUID guestId) {
        return guestService.getGuest(guestId);
    }
}
