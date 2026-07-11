package com.thiago.aidata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thiago.aidata.dto.CacheEntry;
import com.thiago.aidata.dto.CacheEntryInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {

    private static final String CACHE_PREFIX = "cache:response:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
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
        int ttl = input.ttlSeconds() != null ? input.ttlSeconds() : 3600;
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
}
