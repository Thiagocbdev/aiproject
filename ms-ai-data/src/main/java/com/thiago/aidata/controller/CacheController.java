package com.thiago.aidata.controller;

import com.thiago.aidata.dto.CacheEntry;
import com.thiago.aidata.dto.CacheEntryInput;
import com.thiago.aidata.dto.SemanticCacheHit;
import com.thiago.aidata.dto.SemanticCacheLookupRequest;
import com.thiago.aidata.dto.SemanticCachePutRequest;
import com.thiago.aidata.service.CacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
public class CacheController {

    private final CacheService cacheService;

    @GetMapping("/{key}")
    public ResponseEntity<CacheEntry> get(@PathVariable String key) {
        return cacheService.get(key)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void put(@PathVariable String key, @RequestBody CacheEntryInput input) {
        cacheService.put(key, input);
    }

    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String key) {
        cacheService.delete(key);
    }

    @PostMapping("/semantic/lookup")
    public ResponseEntity<SemanticCacheHit> semanticLookup(@RequestBody SemanticCacheLookupRequest request) {
        return cacheService.semanticGet(request.provider(), request.message())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/semantic")
    @ResponseStatus(HttpStatus.CREATED)
    public void semanticPut(@RequestBody SemanticCachePutRequest request) {
        cacheService.semanticPut(request.provider(), request.message(), request.response(), request.ttlSeconds());
    }
}
