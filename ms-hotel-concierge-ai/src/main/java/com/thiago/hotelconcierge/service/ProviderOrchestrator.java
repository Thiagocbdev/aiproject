package com.thiago.hotelconcierge.service;

import com.thiago.hotelconcierge.client.AiDataClient;
import com.thiago.hotelconcierge.model.ProviderCallContext;
import com.thiago.hotelconcierge.tools.HotelTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderOrchestrator {

    @Qualifier("anthropicChatClient") private final ChatClient anthropicClient;
    @Qualifier("openAiChatClient")    private final ChatClient openAiClient;
    @Qualifier("ollamaChatClient")    private final ChatClient ollamaClient;
    private final SseSessionStore sessionStore;
    private final AiDataClient aiDataClient;
    private final HotelTools hotelTools;

    @Value("${concierge.temperature.booking:0.15}") private double bookingTemp;
    @Value("${concierge.temperature.faq:0.35}")     private double faqTemp;
    @Value("${concierge.temperature.recommendation:0.80}") private double recommendationTemp;

    public void callProvider(String provider, ProviderCallContext ctx) {
        long startMs = System.currentTimeMillis();
        String responseText = "";
        int tokensIn = 0, tokensOut = 0;
        boolean cacheHit = false;
        boolean ragUsed = false;

        try {
            HotelTools.setContext(ctx.requestId(), provider);
            String cacheKey = provider + ":" + Math.abs(ctx.message().hashCode());

            // ── STEP 1: cache check ──────────────────────────────────
            log.info("[{}][STEP 1/7] verificando cache (key={})", provider, cacheKey);
            Map<String, Object> cached = safeGetCache(cacheKey);

            if (cached != null && cached.containsKey("response")) {
                cacheHit = true;
                responseText = (String) cached.get("response");
                log.info("[{}][STEP 2/3] CACHE HIT — emitindo resposta cached", provider);
                sessionStore.emit(ctx.requestId(), "cache_hit", Map.of("provider", provider));
                emitTokens(ctx.requestId(), provider, responseText);
                log.info("[{}][STEP 3/3] CONCLUÍDO via cache em {}ms", provider, System.currentTimeMillis() - startMs);

            } else {
                log.info("[{}][STEP 2/7] CACHE MISS — iniciando pipeline LLM", provider);

                // ── STEP 3: RAG ─────────────────────────────────────
                String ragContext;
                if (provider.equals("ollama")) {
                    log.info("[{}][STEP 3/7] RAG: ignorado para provider local (ollama → apenas cache)", provider);
                    ragContext = "";
                } else {
                    log.info("[{}][STEP 3/7] RAG: buscando chunks de contexto", provider);
                    ragContext = performRagSearch(ctx.requestId(), provider, ctx.message());
                }
                ragUsed = !ragContext.isBlank();

                // ── STEP 4: build message ────────────────────────────
                log.info("[{}][STEP 4/7] construindo mensagem (histórico={} rag={})",
                    provider,
                    ctx.contextHistory().isBlank() ? "N" : "Y",
                    ragUsed ? ragContext.length() + " chars" : "N");
                String fullMessage = buildFullMessage(ctx.message(), ctx.contextHistory(), ragContext);

                // ── STEP 5: LLM call ─────────────────────────────────
                double temp = resolveTemperature(provider);
                log.info("[{}][STEP 5/7] chamando LLM (temperature={})", provider, temp);
                long llmStart = System.currentTimeMillis();
                ChatResponse response = callLlm(provider, fullMessage);

                if (response != null && response.getResult() != null) {
                    responseText = response.getResult().getOutput().getText();
                    if (responseText == null) responseText = "";

                    var usage = response.getMetadata().getUsage();
                    tokensIn  = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                    tokensOut = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

                    log.info("[{}][STEP 5/7] LLM respondeu em {}ms — {}↑ {}↓ tokens",
                        provider, System.currentTimeMillis() - llmStart, tokensIn, tokensOut);
                    emitTokens(ctx.requestId(), provider, responseText);

                    // ── STEP 6: cache save ───────────────────────────
                    String trimmed = responseText.trim();
                    if (!trimmed.isBlank() && !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                        log.info("[{}][STEP 6/7] armazenando resposta no cache (TTL=3600s)", provider);
                        safePutCache(cacheKey, responseText, provider);
                    } else {
                        log.info("[{}][STEP 6/7] resposta JSON/vazia — não armazenada no cache", provider);
                    }
                } else {
                    log.warn("[{}][STEP 5/7] LLM retornou resposta nula/erro", provider);
                }

                log.info("[{}][STEP 7/7] CONCLUÍDO em {}ms — disparando saves assíncronos",
                    provider, System.currentTimeMillis() - startMs);
            }

            long durationMs = System.currentTimeMillis() - startMs;
            double temperature = resolveTemperature(provider);
            sessionStore.emit(ctx.requestId(), "metrics", Map.of(
                "provider", provider,
                "tokensIn", tokensIn,
                "tokensOut", tokensOut,
                "temperature", temperature,
                "ragUsed", ragUsed,
                "toolsUsed", List.of(),
                "cacheHit", cacheHit,
                "durationMs", durationMs
            ));
            sessionStore.emit(ctx.requestId(), "done", Map.of("provider", provider));

            final String finalResponse = responseText;
            Thread.ofVirtual().start(() -> safeTrainingSave(provider, ctx.message(), finalResponse));

            if (ctx.turnId() != null && ctx.sessionId() != null) {
                final int fi = tokensIn, fo = tokensOut;
                final boolean fCacheHit = cacheHit, fRagUsed = ragUsed;
                Thread.ofVirtual().start(() ->
                    safeSaveTurnResponse(ctx.sessionId(), ctx.turnId(), provider,
                        finalResponse, fi, fo, fCacheHit, fRagUsed, durationMs)
                );
            }

        } catch (Exception e) {
            log.error("[{}] ERRO no pipeline: {}", provider, e.getMessage(), e);
            sessionStore.emit(ctx.requestId(), "error", Map.of("provider", provider, "message", e.getMessage()));
        } finally {
            HotelTools.clearContext();
            ctx.latch().countDown();
        }
    }

    private String performRagSearch(String requestId, String provider, String message) {
        try {
            String preview = message.length() > 60 ? message.substring(0, 60) + "..." : message;
            log.info("[{}] RAG: query='{}'", provider, preview);
            List<Map<String, Object>> chunks = aiDataClient.searchVectors(Map.of("query", message, "topK", 3));
            if (chunks == null || chunks.isEmpty()) {
                log.info("[{}] RAG: 0 chunks encontrados", provider);
                return "";
            }
            log.info("[{}] RAG: {} chunks encontrados", provider, chunks.size());
            StringBuilder ctx = new StringBuilder();
            for (Map<String, Object> chunk : chunks) {
                if (chunk.containsKey("content")) ctx.append(chunk.get("content")).append("\n");
            }
            sessionStore.emit(requestId, "rag_search", Map.of(
                "provider", provider, "query", message,
                "chunksFound", chunks.size(),
                "chunks", chunks.stream().map(c -> c.getOrDefault("content", "")).toList()
            ));
            return ctx.toString().trim();
        } catch (Exception e) {
            log.debug("[{}] RAG indisponível: {}", provider, e.getMessage());
            return "";
        }
    }

    private String buildFullMessage(String message, String contextHistory, String ragContext) {
        StringBuilder sb = new StringBuilder();
        if (!contextHistory.isBlank()) {
            sb.append("[Histórico da conversa]:\n").append(contextHistory).append("\n\n");
        }
        if (!ragContext.isBlank()) {
            sb.append("[Contexto do hotel]:\n").append(ragContext).append("\n\n");
        }
        sb.append(message);
        return sb.toString();
    }

    private ChatResponse callLlm(String provider, String fullMessage) {
        try {
            return resolveClient(provider).prompt()
                .user(fullMessage)
                .options(buildOptions(provider, resolveTemperature(provider)))
                .tools(hotelTools)
                .call()
                .chatResponse();
        } catch (Exception e) {
            log.warn("[{}] chamada LLM falhou: {}", provider, e.getMessage());
            return null;
        }
    }

    private void emitTokens(String requestId, String provider, String text) {
        if (text == null || text.isBlank()) return;
        String[] words = text.split("(?<=\\s)|(?=\\s)");
        for (String word : words) {
            sessionStore.emit(requestId, "token", Map.of("provider", provider, "chunk", word));
        }
    }

    private Map<String, Object> safeGetCache(String key) {
        try { return aiDataClient.getCache(key); } catch (Exception e) { return null; }
    }

    private void safePutCache(String key, String response, String provider) {
        try {
            aiDataClient.putCache(key, Map.of("response", response, "provider", provider, "ttlSeconds", 3600));
        } catch (Exception e) {
            log.debug("[{}] cache put falhou: {}", provider, e.getMessage());
        }
    }

    private void safeTrainingSave(String provider, String message, String response) {
        try {
            aiDataClient.saveTrainingExample(Map.of(
                "provider", provider, "message", message, "response", response, "toolsUsed", List.of()
            ));
        } catch (Exception e) {
            log.debug("[{}] training save falhou: {}", provider, e.getMessage());
        }
    }

    private void safeSaveTurnResponse(String sessionId, Long turnId, String provider,
                                      String responseText, int tokensIn, int tokensOut,
                                      boolean cacheHit, boolean ragUsed, long durationMs) {
        try {
            aiDataClient.saveTurnResponse(sessionId, turnId, Map.of(
                "provider", provider, "responseText", responseText,
                "tokensIn", tokensIn, "tokensOut", tokensOut,
                "cacheHit", cacheHit, "ragUsed", ragUsed,
                "toolsUsed", List.of(), "durationMs", durationMs
            ));
        } catch (Exception e) {
            log.debug("[{}] turn response save falhou: {}", provider, e.getMessage());
        }
    }

    private ChatClient resolveClient(String provider) {
        return switch (provider) {
            case "openai" -> openAiClient;
            case "ollama" -> ollamaClient;
            default -> anthropicClient;
        };
    }

    private double resolveTemperature(String provider) {
        return switch (provider) {
            case "openai" -> faqTemp;
            case "ollama" -> recommendationTemp;
            default -> bookingTemp;
        };
    }

    private ChatOptions buildOptions(String provider, double temperature) {
        return switch (provider) {
            case "ollama" -> OllamaChatOptions.builder().temperature(temperature).build();
            default -> OpenAiChatOptions.builder().temperature(temperature).build();
        };
    }
}
