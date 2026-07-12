package com.thiago.hotelconcierge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SseSessionStore {
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> pendingActions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public SseEmitter createEmitter(String requestId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onCompletion(() -> emitters.remove(requestId));
        emitter.onTimeout(() -> emitters.remove(requestId));
        emitter.onError(e -> emitters.remove(requestId));
        emitters.put(requestId, emitter);
        return emitter;
    }

    public SseEmitter getEmitter(String requestId) {
        return emitters.get(requestId);
    }

    public void emit(String requestId, String eventType, Object data) {
        SseEmitter emitter = emitters.get(requestId);
        if (emitter == null) return;
        try {
            String json = mapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventType).data(json));
        } catch (IOException e) {
            log.debug("SSE emit failed for request {}: {}", requestId, e.getMessage());
            emitters.remove(requestId);
        }
    }

    public void complete(String requestId) {
        SseEmitter emitter = emitters.remove(requestId);
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    public void storePendingAction(String pendingActionId, Map<String, Object> bookingArgs) {
        pendingActions.put(pendingActionId, bookingArgs);
    }

    public Map<String, Object> removePendingAction(String pendingActionId) {
        return pendingActions.remove(pendingActionId);
    }
}
