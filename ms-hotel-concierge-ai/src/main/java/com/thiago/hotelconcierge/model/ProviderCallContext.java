package com.thiago.hotelconcierge.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * T5: {@code historyTurns} carrega os turnos CRUS da sessão (como retornados por
 * ai-data GET /sessions/{id}/turns). O ProviderOrchestrator monta o histórico
 * por provider a partir deles — cada LLM vê só as próprias respostas.
 * Lista vazia quando useContext=false ou quando não há histórico.
 */
public record ProviderCallContext(
    String requestId,
    String message,
    List<Map<String, Object>> historyTurns,
    String sessionId,
    Long turnId,
    CountDownLatch latch
) {}
