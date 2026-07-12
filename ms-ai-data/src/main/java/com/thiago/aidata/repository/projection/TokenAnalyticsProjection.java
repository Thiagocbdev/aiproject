package com.thiago.aidata.repository.projection;

import java.time.LocalDate;

public interface TokenAnalyticsProjection {
    String getProvider();
    LocalDate getDay();
    Long getTokensInTotal();
    Long getTokensOutTotal();
    Long getCalls();
    Long getCacheHits();
}
