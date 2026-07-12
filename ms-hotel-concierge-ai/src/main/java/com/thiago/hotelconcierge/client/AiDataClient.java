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

    @PostMapping("/api/v1/training/examples")
    void saveTrainingExample(@RequestBody Map<String, Object> body);

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
