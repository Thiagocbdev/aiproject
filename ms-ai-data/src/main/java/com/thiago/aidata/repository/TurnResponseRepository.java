package com.thiago.aidata.repository;

import com.thiago.aidata.model.TurnResponseEntity;
import com.thiago.aidata.repository.projection.TokenAnalyticsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TurnResponseRepository extends JpaRepository<TurnResponseEntity, Long> {

    List<TurnResponseEntity> findByTurnIdIn(List<Long> turnIds);

    @Query(value = """
        SELECT
            tr.provider                                              AS provider,
            CAST(tr.created_at AS DATE)                             AS day,
            SUM(tr.tokens_in)                                       AS tokensInTotal,
            SUM(tr.tokens_out)                                      AS tokensOutTotal,
            COUNT(*)                                                AS calls,
            SUM(CASE WHEN tr.cache_hit THEN 1 ELSE 0 END)          AS cacheHits
        FROM turn_responses tr
        WHERE tr.created_at >= NOW() - CAST(:days || ' days' AS INTERVAL)
        GROUP BY tr.provider, CAST(tr.created_at AS DATE)
        ORDER BY CAST(tr.created_at AS DATE) DESC, tr.provider
        """, nativeQuery = true)
    List<TokenAnalyticsProjection> findTokenAnalytics(@Param("days") int days);

    @Query(value = """
        SELECT
            COUNT(*) FILTER (WHERE cache_hit = TRUE)                               AS cacheHitCount,
            COALESCE(SUM(tokens_in) FILTER (WHERE cache_hit = FALSE), 0)           AS actualTokensSpent,
            COALESCE(
                COUNT(*) FILTER (WHERE cache_hit = TRUE)
                * AVG(tokens_in) FILTER (WHERE cache_hit = FALSE AND tokens_in > 0),
            0)                                                                     AS estimatedSavedTokens
        FROM turn_responses
        """, nativeQuery = true)
    CacheSavingsRaw findCacheSavings();

    interface CacheSavingsRaw {
        Long getCacheHitCount();
        Long getActualTokensSpent();
        Double getEstimatedSavedTokens();
    }
}
