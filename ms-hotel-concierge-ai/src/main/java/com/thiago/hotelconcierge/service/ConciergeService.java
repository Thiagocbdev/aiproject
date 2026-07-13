package com.thiago.hotelconcierge.service;

import com.thiago.hotelconcierge.client.AiDataClient;
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
    private final AiDataClient aiDataClient;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AskAccepted ask(AskRequest request) {
        String requestId = UUID.randomUUID().toString();
        String sessionId = request.sessionId() != null && !request.sessionId().isBlank()
            ? request.sessionId()
            : requestId;

        List<String> providers = (request.providers() != null && !request.providers().isEmpty())
            ? request.providers()
            : List.of("anthropic", "openai", "ollama");

        Long turnId = initSession(sessionId, request);
        String contextHistory = request.useContext() ? loadContextHistory(sessionId, providers) : "";

        // Emitter é criado lazily em stream() — eventos ficam em buffer até o cliente ligar
        virtualThreadExecutor.submit(() -> fanOut(requestId, request.message(), contextHistory, sessionId, turnId, providers));

        return new AskAccepted(requestId, "/api/v1/concierge/stream/" + requestId);
    }

    public SseEmitter stream(String requestId) {
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

    private Long initSession(String sessionId, AskRequest request) {
        try {
            aiDataClient.ensureSession(sessionId);
            Map<String, Object> turnResult = aiDataClient.createTurn(sessionId, Map.of(
                "question", request.message(),
                "useContext", request.useContext(),
                "turnNumber", 0
            ));
            Object turnId = turnResult.get("turnId");
            return turnId instanceof Number n ? n.longValue() : null;
        } catch (Exception e) {
            log.warn("Could not init session in ai-data: {}", e.getMessage());
            return null;
        }
    }

    private String loadContextHistory(String sessionId, List<String> providers) {
        try {
            List<Map<String, Object>> turns = aiDataClient.getTurns(sessionId);
            if (turns == null || turns.isEmpty()) return "";

            StringBuilder history = new StringBuilder();
            for (Map<String, Object> turn : turns) {
                String question = (String) turn.getOrDefault("question", "");
                history.append("[Turno ").append(turn.get("turnNumber")).append("]\n");
                history.append("Usuário: ").append(question).append("\n");

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> responses = (List<Map<String, Object>>) turn.getOrDefault("responses", List.of());
                for (Map<String, Object> resp : responses) {
                    String p = (String) resp.getOrDefault("provider", "");
                    if (providers.contains(p)) {
                        history.append(p).append(": ").append(resp.getOrDefault("responseText", "")).append("\n");
                    }
                }
                history.append("\n");
            }
            return history.toString().trim();
        } catch (Exception e) {
            log.warn("Could not load context history: {}", e.getMessage());
            return "";
        }
    }

    private void fanOut(String requestId, String message, String contextHistory,
                        String sessionId, Long turnId, List<String> providers) {
        CountDownLatch latch = new CountDownLatch(providers.size());
        for (String provider : providers) {
            ProviderCallContext ctx = new ProviderCallContext(requestId, message, contextHistory, sessionId, turnId, latch);
            virtualThreadExecutor.submit(() -> orchestrator.callProvider(provider, ctx));
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
