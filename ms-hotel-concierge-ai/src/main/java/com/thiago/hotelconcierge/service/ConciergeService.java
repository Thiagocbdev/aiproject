package com.thiago.hotelconcierge.service;

import com.thiago.hotelconcierge.client.HotelInfoClient;
import com.thiago.hotelconcierge.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConciergeService {
    private final SseSessionStore sessionStore;
    private final ProviderOrchestrator orchestrator;
    private final HotelInfoClient hotelInfoClient;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AskAccepted ask(AskRequest request) {
        String requestId = UUID.randomUUID().toString();
        List<String> providers = (request.providers() != null && !request.providers().isEmpty())
            ? request.providers()
            : List.of("anthropic", "openai", "ollama");

        // Pre-create emitter BEFORE fan-out so fast-failing providers don't lose events
        sessionStore.createEmitter(requestId);

        // Fan-out in background
        virtualThreadExecutor.submit(() -> fanOut(requestId, request.message(), providers));

        return new AskAccepted(requestId, "/api/v1/concierge/stream/" + requestId);
    }

    public SseEmitter stream(String requestId) {
        // Return pre-created emitter if it exists, otherwise create new one
        SseEmitter existing = sessionStore.getEmitter(requestId);
        return existing != null ? existing : sessionStore.createEmitter(requestId);
    }

    public ConfirmBookingResponse confirmBooking(ConfirmBookingRequest request) {
        if (!request.confirm()) {
            sessionStore.removePendingAction(request.pendingActionId());
            return new ConfirmBookingResponse(null, "cancelled");
        }

        Map<String, Object> bookingArgs = sessionStore.removePendingAction(request.pendingActionId());
        if (bookingArgs == null) {
            throw new IllegalArgumentException("Pending action not found or already processed: " + request.pendingActionId());
        }

        try {
            Map<String, Object> result = hotelInfoClient.createBooking(bookingArgs);
            String bookingId = result.getOrDefault("id", "unknown").toString();
            return new ConfirmBookingResponse(bookingId, "confirmed");
        } catch (Exception e) {
            log.error("Failed to create booking: {}", e.getMessage());
            throw new RuntimeException("Failed to create booking: " + e.getMessage());
        }
    }

    private void fanOut(String requestId, String message, List<String> providers) {
        CountDownLatch latch = new CountDownLatch(providers.size());
        for (String provider : providers) {
            virtualThreadExecutor.submit(() -> orchestrator.callProvider(provider, message, requestId, latch));
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sessionStore.complete(requestId);
        }
    }
}
