package com.thiago.hotelconcierge.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "ai-data", url = "${concierge.ai-data-url}")
public interface AiDataClient {

    @PostMapping("/api/v1/vectors/search")
    List<Map<String, Object>> searchVectors(@RequestBody Map<String, Object> request);

    @GetMapping("/api/v1/cache/{key}")
    Map<String, Object> getCache(@PathVariable("key") String key);

    @PutMapping("/api/v1/cache/{key}")
    void putCache(@PathVariable("key") String key, @RequestBody Map<String, Object> body);

    /** HIT: 200 {response, score, semantic}; MISS: 404 sem body (FeignException.NotFound). */
    @PostMapping("/api/v1/cache/semantic/lookup")
    Map<String, Object> semanticCacheLookup(@RequestBody Map<String, Object> body);

    @PostMapping("/api/v1/cache/semantic")
    void putSemanticCache(@RequestBody Map<String, Object> body);

    @PostMapping("/api/v1/training/examples")
    void saveTrainingExample(@RequestBody Map<String, Object> body);

    /** T6: exemplos top-rated (rating >= 4, rating DESC + createdAt DESC); limit default 2, teto 10. */
    @GetMapping("/api/v1/training/examples/top")
    List<Map<String, Object>> getTopTrainingExamples(
        @RequestParam("provider") String provider,
        @RequestParam("limit") int limit
    );

    @PostMapping("/api/v1/sessions/{sessionId}")
    void ensureSession(@PathVariable("sessionId") String sessionId);

    @PostMapping("/api/v1/sessions/{sessionId}/turns")
    Map<String, Object> createTurn(
        @PathVariable("sessionId") String sessionId,
        @RequestBody Map<String, Object> body
    );

    @PostMapping("/api/v1/sessions/{sessionId}/turns/{turnId}/responses")
    void saveTurnResponse(
        @PathVariable("sessionId") String sessionId,
        @PathVariable("turnId") Long turnId,
        @RequestBody Map<String, Object> body
    );

    @GetMapping("/api/v1/sessions/{sessionId}/turns")
    List<Map<String, Object>> getTurns(@PathVariable("sessionId") String sessionId);
}
