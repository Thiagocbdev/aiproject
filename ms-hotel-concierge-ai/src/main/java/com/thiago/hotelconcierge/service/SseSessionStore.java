package com.thiago.hotelconcierge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stores SSE emitters and buffers all events per requestId so that late-joining
 * clients (e.g. when a cache hit completes in ~100ms before the browser connects)
 * receive a full replay.
 *
 * Flow:
 *   ask()    → submits fanOut (no emitter yet); events go into replayBuffer
 *   stream() → createEmitter() → replays buffer; future events go to live emitter
 *   complete() → marks done; if no live emitter the buffer stays for the next stream() call
 */
@Component
@Slf4j
public class SseSessionStore {
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> pendingActions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<SseEvent>> replayBuffer = new ConcurrentHashMap<>();
    private final Set<String> completedRequests = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper = new ObjectMapper();

    private record SseEvent(String eventType, String json) {}

    /**
     * Called by stream() when the client connects.
     * Replays any buffered events first, then registers the emitter for live events.
     * If processing was already done, replays and immediately completes.
     */
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
            completedRequests.remove(requestId);
        });
        emitter.onError(e -> emitters.remove(requestId));

        // Register BEFORE replay so concurrent emit() calls land on this emitter
        emitters.put(requestId, emitter);

        // Replay buffered events
        List<SseEvent> buffered = replayBuffer.getOrDefault(requestId, List.of());
        for (SseEvent event : buffered) {
            try {
                emitter.send(SseEmitter.event().name(event.eventType()).data(event.json()));
            } catch (IOException ignored) {}
        }

        // If fanOut already finished, complete immediately after replay
        if (completedRequests.contains(requestId)) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }

        return emitter;
    }

    public SseEmitter getEmitter(String requestId) {
        return emitters.get(requestId);
    }

    /**
     * Emit an event: always buffer it AND send to the live emitter (if connected).
     */
    public void emit(String requestId, String eventType, Object data) {
        try {
            String json = mapper.writeValueAsString(data);
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

    /**
     * Mark processing as done.
     * - If a live emitter exists: complete it (onCompletion cleans everything up).
     * - If no emitter yet: mark completedRequests so createEmitter() will complete on connect.
     */
    public void complete(String requestId) {
        completedRequests.add(requestId);
        SseEmitter emitter = emitters.get(requestId);
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception ignored) {}
            // onCompletion callback will clean up emitters, replayBuffer, completedRequests
        }
        // If emitter is null: replayBuffer and completedRequests stay for lazy createEmitter()
    }

    public void storePendingAction(String pendingActionId, Map<String, Object> bookingArgs) {
        pendingActions.put(pendingActionId, bookingArgs);
    }

    public Map<String, Object> removePendingAction(String pendingActionId) {
        return pendingActions.remove(pendingActionId);
    }
}
