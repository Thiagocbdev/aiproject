package com.thiago.hotelconcierge.service;

import com.thiago.hotelconcierge.client.AiDataClient;
import com.thiago.hotelconcierge.model.ProviderCallContext;
import com.thiago.hotelconcierge.tools.HotelTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
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
        List<String> toolsUsed = List.of();

        try {
            HotelTools.setContext(ctx.requestId(), provider);

            String cacheKey = provider + ":" + Math.abs(ctx.message().hashCode());

            // GAP-01: check cache before calling LLM
            Map<String, Object> cached = safeGetCache(cacheKey);
            if (cached != null && cached.containsKey("response")) {
                cacheHit = true;
                responseText = (String) cached.get("response");
                sessionStore.emit(ctx.requestId(), "cache_hit", Map.of("provider", provider));
                emitTokens(ctx.requestId(), provider, responseText);
            } else {
                // RAG search
                String ragContext = performRagSearch(ctx.requestId(), provider, ctx.message());
                ragUsed = !ragContext.isBlank();

                String fullMessage = buildFullMessage(ctx.message(), ctx.contextHistory(), ragContext);

                ChatResponse response = callLlm(provider, fullMessage);
                toolsUsed = HotelTools.getAndClearToolsUsed();

                if (response != null && response.getResult() != null) {
                    responseText = response.getResult().getOutput().getText();
                    if (responseText == null) responseText = "";

                    var usage = response.getMetadata().getUsage();
                    tokensIn  = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                    tokensOut = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

                    emitTokens(ctx.requestId(), provider, responseText);

                    // GAP-01: cache the response
                    if (!responseText.isBlank()) {
                        safePutCache(cacheKey, responseText, provider);
                    }
                } else {
                    // LLM failed (offline, no API key, etc.) — surface the error visually
                    sessionStore.emit(ctx.requestId(), "error", Map.of(
                        "provider", provider,
                        "message", "LLM indisponível ou sem API key configurada"
                    ));
                }
            }

            long durationMs = System.currentTimeMillis() - startMs;
            double temperature = resolveTemperature(provider);
            sessionStore.emit(ctx.requestId(), "metrics", Map.of(
                "provider", provider,
                "tokensIn", tokensIn,
                "tokensOut", tokensOut,
                "temperature", temperature,
                "ragUsed", ragUsed,
                "toolsUsed", toolsUsed,
                "cacheHit", cacheHit,
                "durationMs", durationMs
            ));
            sessionStore.emit(ctx.requestId(), "done", Map.of("provider", provider));

            // GAP-02: save training example (fire-and-forget)
            final String finalResponse = responseText;
            Thread.ofVirtual().start(() ->
                safeTrainingSave(provider, ctx.message(), finalResponse)
            );

            // GAP-04: save turn response to DB
            if (ctx.turnId() != null && ctx.sessionId() != null) {
                final int fi = tokensIn, fo = tokensOut;
                final boolean fCacheHit = cacheHit, fRagUsed = ragUsed;
                final List<String> fToolsUsed = toolsUsed;
                Thread.ofVirtual().start(() ->
                    safeSaveTurnResponse(ctx.sessionId(), ctx.turnId(), provider,
                        finalResponse, fi, fo, fCacheHit, fRagUsed, fToolsUsed, durationMs)
                );
            }

        } catch (Exception e) {
            log.error("Error in provider {}: {}", provider, e.getMessage(), e);
            sessionStore.emit(ctx.requestId(), "error", Map.of("provider", provider, "message", e.getMessage()));
        } finally {
            HotelTools.clearContext();
            ctx.latch().countDown();
        }
    }

    private String performRagSearch(String requestId, String provider, String message) {
        try {
            List<Map<String, Object>> chunks = aiDataClient.searchVectors(Map.of("query", message, "topK", 3));
            if (chunks == null || chunks.isEmpty()) return "";

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
            log.debug("RAG search unavailable: {}", e.getMessage());
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
            log.warn("LLM call failed for provider {}: {}", provider, e.getMessage());
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
        try {
            return aiDataClient.getCache(key);
        } catch (Exception e) {
            return null;
        }
    }

    private void safePutCache(String key, String response, String provider) {
        try {
            aiDataClient.putCache(key, Map.of("response", response, "provider", provider, "ttlSeconds", 3600));
        } catch (Exception e) {
            log.debug("Cache put failed: {}", e.getMessage());
        }
    }

    private void safeTrainingSave(String provider, String message, String response) {
        try {
            aiDataClient.saveTrainingExample(Map.of(
                "provider", provider,
                "message", message,
                "response", response,
                "toolsUsed", List.of()
            ));
        } catch (Exception e) {
            log.debug("Training save failed: {}", e.getMessage());
        }
    }

    private void safeSaveTurnResponse(String sessionId, Long turnId, String provider,
                                      String responseText, int tokensIn, int tokensOut,
                                      boolean cacheHit, boolean ragUsed, List<String> toolsUsed, long durationMs) {
        try {
            aiDataClient.saveTurnResponse(sessionId, turnId, Map.of(
                "provider", provider,
                "responseText", responseText,
                "tokensIn", tokensIn,
                "tokensOut", tokensOut,
                "cacheHit", cacheHit,
                "ragUsed", ragUsed,
                "toolsUsed", toolsUsed,
                "durationMs", durationMs
            ));
        } catch (Exception e) {
            log.debug("Turn response save failed: {}", e.getMessage());
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
            case "openai" -> OpenAiChatOptions.builder().temperature(temperature).build();
            case "ollama" -> OllamaChatOptions.builder().temperature(temperature).build();
            default -> AnthropicChatOptions.builder().temperature(temperature).build();
        };
    }

}
