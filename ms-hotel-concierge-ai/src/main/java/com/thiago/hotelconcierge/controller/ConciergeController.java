package com.thiago.hotelconcierge.controller;

import com.thiago.hotelconcierge.model.*;
import com.thiago.hotelconcierge.service.ConciergeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/concierge")
@RequiredArgsConstructor
public class ConciergeController {
    private final ConciergeService conciergeService;

    @PostMapping("/ask")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AskAccepted ask(@Valid @RequestBody AskRequest request) {
        return conciergeService.ask(request);
    }

    @GetMapping(value = "/stream/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String requestId) {
        return conciergeService.stream(requestId);
    }

    @PostMapping("/confirm-booking")
    public ResponseEntity<?> confirmBooking(@Valid @RequestBody ConfirmBookingRequest request) {
        try {
            return ResponseEntity.ok(conciergeService.confirmBooking(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage()));
        }
    }
}
