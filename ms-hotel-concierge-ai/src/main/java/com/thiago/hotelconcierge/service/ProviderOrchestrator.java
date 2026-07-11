package com.thiago.hotelconcierge.service;

import com.thiago.hotelconcierge.client.AiDataClient;
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
import java.util.concurrent.CountDownLatch;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderOrchestrator {
    @Qualifier("anthropicChatClient") private final ChatClient anthropicClient;
    @Qualifier("openAiChatClient") private final ChatClient openAiClient;
    @Qualifier("ollamaChatClient") private final ChatClient ollamaClient;
    private final SseSessionStore sessionStore;
    private final AiDataClient aiDataClient;
    private final HotelTools hotelTools;

    @Value("${concierge.temperature.booking:0.15}") private double bookingTemp;
    @Value("${concierge.temperature.faq:0.35}") private double faqTemp;
    @Value("${concierge.temperature.recommendation:0.80}") private double recommendationTemp;

    public void callProvider(String provider, String message, String requestId, CountDownLatch latch) {
        try {
            HotelTools.setContext(requestId, provider);

            // RAG search
            String ragContext = performRagSearch(requestId, provider, message);

            String fullMessage = ragContext.isBlank() ? message :
                message + "\n\n[Contexto do hotel]:\n" + ragContext;

            ChatClient client = resolveClient(provider);
            double temperature = resolveTemperature(provider);

            ChatResponse response;
            try {
                response = client.prompt()
                    .user(fullMessage)
                    .options(buildOptions(provider, temperature))
                    .tools(hotelTools)
                    .call()
                    .chatResponse();
            } catch (Exception llmError) {
                log.warn("LLM call failed for provider {}: {}", provider, llmError.getMessage());
                sessionStore.emit(requestId, "error", Map.of(
                    "provider", provider,
                    "message", "Provider offline ou erro na chamada: " + llmError.getMessage()
                ));
                return;
            }

            if (response != null && response.getResult() != null) {
                String content = response.getResult().getOutput().getText();
                if (content != null && !content.isBlank()) {
                    // Emit content as tokens (split by words for progressive display)
                    String[] words = content.split("(?<=\\s)|(?=\\s)");
                    for (String word : words) {
                        sessionStore.emit(requestId, "token", Map.of("provider", provider, "chunk", word));
                    }
                }

                // Emit metrics
                var usage = response.getMetadata().getUsage();
                Map<String, Object> metrics = Map.of(
                    "provider", provider,
                    "tokensIn", usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
                    "tokensOut", usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0,
                    "temperature", temperature,
                    "ragUsed", !ragContext.isBlank(),
                    "toolsUsed", List.of()
                );
                sessionStore.emit(requestId, "metrics", metrics);
            }

            sessionStore.emit(requestId, "done", Map.of("provider", provider));

        } catch (Exception e) {
            log.error("Error in provider {}: {}", provider, e.getMessage(), e);
            sessionStore.emit(requestId, "error", Map.of("provider", provider, "message", e.getMessage()));
        } finally {
            HotelTools.clearContext();
            latch.countDown();
        }
    }

    private String performRagSearch(String requestId, String provider, String message) {
        try {
            Map<String, Object> searchRequest = Map.of("query", message, "topK", 3);
            List<Map<String, Object>> chunks = aiDataClient.searchVectors(searchRequest);

            if (chunks == null || chunks.isEmpty()) return "";

            StringBuilder ctx = new StringBuilder();
            for (Map<String, Object> chunk : chunks) {
                if (chunk.containsKey("content")) {
                    ctx.append(chunk.get("content")).append("\n");
                }
            }

            sessionStore.emit(requestId, "rag_search", Map.of(
                "provider", provider,
                "query", message,
                "chunksFound", chunks.size(),
                "chunks", chunks.stream().map(c -> c.getOrDefault("content", "")).toList()
            ));

            return ctx.toString().trim();
        } catch (Exception e) {
            log.debug("RAG search unavailable: {}", e.getMessage());
            return "";
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
