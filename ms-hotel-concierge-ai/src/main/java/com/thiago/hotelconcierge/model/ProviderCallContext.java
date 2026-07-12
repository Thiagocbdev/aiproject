package com.thiago.hotelconcierge.model;

import java.util.concurrent.CountDownLatch;

public record ProviderCallContext(
    String requestId,
    String message,
    String contextHistory,
    String sessionId,
    Long turnId,
    CountDownLatch latch
) {}
