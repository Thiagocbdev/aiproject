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
}
