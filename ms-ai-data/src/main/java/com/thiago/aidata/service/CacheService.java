package com.thiago.aidata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thiago.aidata.dto.CacheEntry;
import com.thiago.aidata.dto.CacheEntryInput;
import com.thiago.aidata.dto.SemanticCacheHit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {

    private static final String CACHE_PREFIX = "cache:response:";
    private static final int DEFAULT_TTL_SECONDS = 3600;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final VectorStore semanticCacheVectorStore;
    private final double semanticThreshold;

    public CacheService(RedisTemplate<String, String> redisTemplate,
                        @Qualifier("semanticCacheVectorStore") VectorStore semanticCacheVectorStore,
                        @Value("${cache.semantic-threshold:0.92}") double semanticThreshold) {
        this.redisTemplate = redisTemplate;
        this.semanticCacheVectorStore = semanticCacheVectorStore;
        this.semanticThreshold = semanticThreshold;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public Optional<CacheEntry> get(String key) {
        String value = redisTemplate.opsForValue().get(CACHE_PREFIX + key);
        if (value == null) return Optional.empty();
        try {
            CacheEntry entry = objectMapper.readValue(value, CacheEntry.class);
            Long ttl = redisTemplate.getExpire(CACHE_PREFIX + key, TimeUnit.SECONDS);
            return Optional.of(new CacheEntry(key, entry.response(), entry.provider(), entry.createdAt(), ttl));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cache entry for key: {}", key, e);
            return Optional.empty();
        }
    }

    public void put(String key, CacheEntryInput input) {
        int ttl = input.ttlSeconds() != null ? input.ttlSeconds() : DEFAULT_TTL_SECONDS;
        CacheEntry entry = new CacheEntry(key, input.response(), input.provider(), LocalDateTime.now(), (long) ttl);
        try {
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(CACHE_PREFIX + key, json, ttl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cache entry for key: {}", key, e);
        }
    }

    public void delete(String key) {
        redisTemplate.delete(CACHE_PREFIX + key);
    }

    public Optional<SemanticCacheHit> semanticGet(String provider, String message) {
        SearchRequest searchRequest = SearchRequest.builder()
            .query(message)
            .topK(1)
            .similarityThreshold(semanticThreshold)
            .filterExpression(new FilterExpressionBuilder().eq("provider", provider).build())
            .build();
        List<Document> results;
        try {
            results = semanticCacheVectorStore.similaritySearch(searchRequest);
        } catch (Exception e) {
            log.warn("Semantic cache lookup unavailable (index may not exist): {}", e.getMessage());
            return Optional.empty();
        }
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        Document doc = results.get(0);
        Map<String, Object> metadata = doc.getMetadata();
        if (isExpired(metadata)) {
            log.debug("Semantic cache entry {} expired, evicting", doc.getId());
            try {
                semanticCacheVectorStore.delete(List.of(doc.getId()));
            } catch (Exception e) {
                log.warn("Failed to evict expired semantic cache entry {}: {}", doc.getId(), e.getMessage());
            }
            return Optional.empty();
        }
        String response = metadata.get("response") != null ? String.valueOf(metadata.get("response")) : null;
        Double score = doc.getScore();
        return Optional.of(new SemanticCacheHit(response, score, true));
    }

    public void semanticPut(String provider, String message, String response, Integer ttlSeconds) {
        int ttl = ttlSeconds != null ? ttlSeconds : DEFAULT_TTL_SECONDS;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("provider", provider);
        metadata.put("response", response);
        metadata.put("createdAt", System.currentTimeMillis());
        metadata.put("ttlSeconds", ttl);
        Document doc = new Document(message, metadata);
        semanticCacheVectorStore.add(List.of(doc));
    }

    private boolean isExpired(Map<String, Object> metadata) {
        Long createdAt = asLong(metadata.get("createdAt"));
        if (createdAt == null) {
            return false;
        }
        Long ttlSeconds = asLong(metadata.get("ttlSeconds"));
        long ttl = ttlSeconds != null ? ttlSeconds : DEFAULT_TTL_SECONDS;
        return System.currentTimeMillis() > createdAt + ttl * 1000L;
    }

    private Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return (long) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
