package com.thiago.aidata.service;

import com.thiago.aidata.dto.AnalyticsTokenDto;
import com.thiago.aidata.dto.CacheSavingsDto;
import com.thiago.aidata.repository.TurnResponseRepository;
import com.thiago.aidata.repository.TurnResponseRepository.CacheSavingsRaw;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TurnResponseRepository responseRepository;

    @Transactional(readOnly = true)
    public List<AnalyticsTokenDto> getTokenAnalytics(int days) {
        return responseRepository.findTokenAnalytics(days).stream()
            .map(p -> new AnalyticsTokenDto(
                p.getProvider(),
                p.getDay(),
                nullToZero(p.getTokensInTotal()),
                nullToZero(p.getTokensOutTotal()),
                nullToZero(p.getCalls()),
                nullToZero(p.getCacheHits())
            )).toList();
    }

    @Transactional(readOnly = true)
    public CacheSavingsDto getCacheSavings() {
        CacheSavingsRaw raw = responseRepository.findCacheSavings();
        return new CacheSavingsDto(
            raw.getCacheHitCount() != null ? raw.getCacheHitCount() : 0L,
            raw.getActualTokensSpent() != null ? raw.getActualTokensSpent() : 0L,
            raw.getEstimatedSavedTokens() != null ? raw.getEstimatedSavedTokens().longValue() : 0L
        );
    }

    private long nullToZero(Long val) {
        return val != null ? val : 0L;
    }
}
