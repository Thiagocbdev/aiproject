package com.thiago.hotelconcierge.service;

import com.thiago.hotelconcierge.client.AiDataClient;
import com.thiago.hotelconcierge.client.HotelInfoClient;
import com.thiago.hotelconcierge.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConciergeService {
    private final SseSessionStore sessionStore;
    private final ProviderOrchestrator orchestrator;
    private final HotelInfoClient hotelInfoClient;
    private final AiDataClient aiDataClient;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Ollama local (llama3.2 em CPU) pode levar minutos num prompt com RAG + histórico
    @Value("${concierge.fanout-timeout-seconds:180}")
    private long fanoutTimeoutSeconds;

    public AskAccepted ask(AskRequest request) {
        String requestId = UUID.randomUUID().toString();
        String sessionId = request.sessionId() != null && !request.sessionId().isBlank()
            ? request.sessionId()
            : requestId;

        List<String> providers = (request.providers() != null && !request.providers().isEmpty())
            ? request.providers()
            : List.of("anthropic", "openai", "ollama");

        Long turnId = initSession(sessionId, request);
        // T5: useContext=false → lista vazia (nenhum histórico chega aos providers)
        List<Map<String, Object>> historyTurns = request.useContext() ? loadTurns(sessionId) : List.of();

        // Emitter é criado lazily em stream() — eventos ficam em buffer até o cliente ligar
        virtualThreadExecutor.submit(() -> fanOut(requestId, request.message(), historyTurns, sessionId, turnId, providers));

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

    /**
     * T5: retorna os turnos CRUS da sessão (ordem cronológica, como o ai-data devolve).
     * A montagem do histórico por provider (formato + limites) acontece no
     * ProviderOrchestrator.buildHistoryForProvider — cada LLM vê só as próprias respostas.
     */
    private List<Map<String, Object>> loadTurns(String sessionId) {
        try {
            List<Map<String, Object>> turns = aiDataClient.getTurns(sessionId);
            return turns != null ? turns : List.of();
        } catch (Exception e) {
            log.warn("Could not load context history: {}", e.getMessage());
            return List.of();
        }
    }

    private void fanOut(String requestId, String message, List<Map<String, Object>> historyTurns,
                        String sessionId, Long turnId, List<String> providers) {
        Set<String> finishedProviders = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(providers.size());
        for (String provider : providers) {
            ProviderCallContext ctx = new ProviderCallContext(requestId, message, historyTurns, sessionId, turnId, latch);
            virtualThreadExecutor.submit(() -> {
                orchestrator.callProvider(provider, ctx);
                finishedProviders.add(provider);
            });
        }
        try {
            boolean allDone = latch.await(fanoutTimeoutSeconds, TimeUnit.SECONDS);
            if (!allDone) {
                // 200ms grace: providers that counted down at the exact timeout boundary
                // haven't had CPU time to add themselves to the set yet
                Thread.sleep(200);
                for (String p : providers) {
                    if (!finishedProviders.contains(p)) {
                        log.warn("[fanOut] {} não respondeu em {}s — notificando frontend", p, fanoutTimeoutSeconds);
                        sessionStore.emit(requestId, "error", Map.of(
                            "provider", p,
                            "message", "Tempo limite atingido (" + fanoutTimeoutSeconds + "s) — o modelo local está processando lentamente. Tente novamente."
                        ));
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sessionStore.complete(requestId);
        }
    }
}
