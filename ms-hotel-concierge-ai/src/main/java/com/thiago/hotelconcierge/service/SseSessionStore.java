package com.thiago.hotelconcierge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class SseSessionStore {
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> pendingActions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<SseEvent>> replayBuffer = new ConcurrentHashMap<>();
    private final Set<String> completedRequests = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper = new ObjectMapper();

    private record SseEvent(String eventType, String json) {}

    public SseEmitter createEmitter(String requestId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onCompletion(() -> {
            emitters.remove(requestId);
            replayBuffer.remove(requestId);
            completedRequests.remove(requestId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(requestId);
            replayBuffer.remove(requestId);
        });
        emitter.onError(e -> emitters.remove(requestId));
        emitters.put(requestId, emitter);

        // Replay buffered events for late-joining clients (e.g. cache hits complete before SSE connects)
        if (completedRequests.contains(requestId)) {
            List<SseEvent> buffered = replayBuffer.getOrDefault(requestId, List.of());
            for (SseEvent event : buffered) {
                try {
                    emitter.send(SseEmitter.event().name(event.eventType()).data(event.json()));
                } catch (IOException ignored) {}
            }
            try { emitter.complete(); } catch (Exception ignored) {}
        }

        return emitter;
    }

    public SseEmitter getEmitter(String requestId) {
        return emitters.get(requestId);
    }

    public void emit(String requestId, String eventType, Object data) {
        try {
            String json = mapper.writeValueAsString(data);
            // Always buffer for potential late-joining SSE clients
            replayBuffer.computeIfAbsent(requestId, k -> new CopyOnWriteArrayList<>())
                .add(new SseEvent(eventType, json));

            SseEmitter emitter = emitters.get(requestId);
            if (emitter == null) return;
            emitter.send(SseEmitter.event().name(eventType).data(json));
        } catch (IOException e) {
            log.debug("SSE emit failed for request {}: {}", requestId, e.getMessage());
            emitters.remove(requestId);
        } catch (Exception e) {
            log.debug("SSE serialization failed: {}", e.getMessage());
        }
    }

    public void complete(String requestId) {
        completedRequests.add(requestId);
        SseEmitter emitter = emitters.remove(requestId);
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception ignored) {}
            replayBuffer.remove(requestId);
            completedRequests.remove(requestId);
        }
        // If emitter is null (client not yet connected), buffer stays for replay in createEmitter()
    }

    public void storePendingAction(String pendingActionId, Map<String, Object> bookingArgs) {
        pendingActions.put(pendingActionId, bookingArgs);
    }

    public Map<String, Object> removePendingAction(String pendingActionId) {
        return pendingActions.remove(pendingActionId);
    }
}
