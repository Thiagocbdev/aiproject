package com.thiago.hotelconcierge.service;

import com.thiago.hotelconcierge.client.AiDataClient;
import com.thiago.hotelconcierge.model.ProviderCallContext;
import com.thiago.hotelconcierge.tools.HotelTools;
import feign.FeignException;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
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

    // T4: RAG enxuto para o ollama (llama3.2 3B) — menos chunks e só categorias
    // úteis para recommendation, para não estourar a janela de contexto
    @Value("${concierge.rag.top-k:3}")        private int ragTopK;
    @Value("${concierge.rag.ollama-top-k:2}") private int ollamaRagTopK;
    @Value("${concierge.rag.ollama-categories:local-attractions,restaurant-menu}")
    private List<String> ollamaRagCategories;

    // T5: limites de histórico por provider — truncamento sempre por TURNO INTEIRO
    // (turnos mais antigos que não cabem são descartados; nunca corta no meio)
    @Value("${concierge.history.max-chars:3000}")        private int historyMaxChars;
    @Value("${concierge.history.ollama-max-turns:2}")    private int ollamaHistoryMaxTurns;
    @Value("${concierge.history.ollama-max-chars:1200}") private int ollamaHistoryMaxChars;

    // T6: few-shot com exemplos top-rated (rating >= 4) — SÓ para o ollama (llama3.2 3B),
    // que mais se beneficia de exemplos; anthropic/openai nunca recebem few-shot
    @Value("${concierge.fewshot.enabled:true}")            private boolean fewshotEnabled;
    @Value("${concierge.fewshot.limit:2}")                 private int fewshotLimit;
    @Value("${concierge.fewshot.max-response-chars:300}")  private int fewshotMaxResponseChars;

    public void callProvider(String provider, ProviderCallContext ctx) {
        long startMs = System.currentTimeMillis();
        String responseText = "";
        int tokensIn = 0, tokensOut = 0;
        boolean cacheHit = false;
        boolean ragUsed = false;

        try {
            HotelTools.setContext(ctx.requestId(), provider);
            String cacheKey = cacheKey(provider, ctx.message());

            // ── STEP 1: cache check (exato → semântico) ──────────────
            log.info("[{}][STEP 1/7] verificando cache exato (key={})", provider, cacheKey);
            Map<String, Object> cached = safeGetCache(cacheKey);
            boolean semanticHit = false;
            double semanticScore = 0.0;

            if (cached == null || !cached.containsKey("response")) {
                log.info("[{}][STEP 1/7] miss exato — verificando cache semântico", provider);
                Map<String, Object> semantic = safeSemanticLookup(provider, ctx.message());
                if (semantic != null && semantic.get("response") instanceof String) {
                    semanticHit = true;
                    semanticScore = semantic.get("score") instanceof Number n ? n.doubleValue() : 0.0;
                    cached = semantic;
                }
            }

            if (cached != null && cached.containsKey("response")) {
                cacheHit = true;
                responseText = (String) cached.get("response");
                log.info("[{}][STEP 2/3] CACHE HIT ({}) — emitindo resposta cached",
                    provider, semanticHit ? "semântico score=" + semanticScore : "exato");
                Map<String, Object> hitPayload = semanticHit
                    ? Map.of("provider", provider, "semantic", true, "score", semanticScore)
                    : Map.of("provider", provider, "semantic", false);
                sessionStore.emit(ctx.requestId(), "cache_hit", hitPayload);
                emitTokens(ctx.requestId(), provider, responseText);
                log.info("[{}][STEP 3/3] CONCLUÍDO via cache em {}ms", provider, System.currentTimeMillis() - startMs);

            } else {
                log.info("[{}][STEP 2/7] CACHE MISS — iniciando pipeline LLM", provider);

                // ── STEP 3: RAG ─────────────────────────────────────
                log.info("[{}][STEP 3/7] RAG: buscando chunks de contexto", provider);
                String ragContext = performRagSearch(ctx.requestId(), provider, ctx.message());
                ragUsed = !ragContext.isBlank();

                // ── STEP 4: build message ────────────────────────────
                // T5: histórico montado POR PROVIDER a partir dos turnos crus
                String history = buildHistoryForProvider(provider, ctx.historyTurns());
                // T6: few-shot só para o ollama; fail-safe (falha → prompt sem exemplos)
                String fewShot = safeBuildFewShotExamples(provider);
                log.info("[{}][STEP 4/7] construindo mensagem (few-shot={} histórico={} rag={})",
                    provider,
                    fewShot.isBlank() ? "N" : fewShot.length() + " chars",
                    history.isBlank() ? "N" : history.length() + " chars",
                    ragUsed ? ragContext.length() + " chars" : "N");
                String fullMessage = buildFullMessage(ctx.message(), history, ragContext, fewShot);

                // ── STEP 5: LLM call ─────────────────────────────────
                double temp = resolveTemperature(provider);
                log.info("[{}][STEP 5/7] chamando LLM (temperature={})", provider, temp);
                long llmStart = System.currentTimeMillis();
                ChatResponse response = callLlm(provider, fullMessage);

                if (response != null && response.getResult() != null) {
                    responseText = response.getResult().getOutput().getText();
                    if (responseText == null) responseText = "";

                    // GAP-07: null-safe metadata access
                    var meta  = response.getMetadata();
                    var usage = meta != null ? meta.getUsage() : null;
                    tokensIn  = usage != null && usage.getPromptTokens()      != null ? usage.getPromptTokens()      : 0;
                    tokensOut = usage != null && usage.getCompletionTokens()  != null ? usage.getCompletionTokens()  : 0;

                    log.info("[{}][STEP 5/7] LLM respondeu em {}ms — {}↑ {}↓ tokens",
                        provider, System.currentTimeMillis() - llmStart, tokensIn, tokensOut);

                    // ── STEP 6: cache save ───────────────────────────
                    String trimmed = responseText.trim();
                    if (!trimmed.isBlank()) {
                        log.info("[{}][STEP 6/7] armazenando resposta no cache exato + semântico (TTL=48h)", provider);
                        safePutCache(cacheKey, responseText, provider);
                        safePutSemanticCache(provider, ctx.message(), responseText);
                        emitTokens(ctx.requestId(), provider, responseText);
                    } else {
                        // GAP-06: emit visible error when LLM responds but content is empty
                        log.warn("[{}][STEP 6/7] resposta vazia — notificando front", provider);
                        sessionStore.emit(ctx.requestId(), "error", Map.of("provider", provider,
                            "message", "O modelo não gerou uma resposta. Tente reformular a pergunta."));
                    }
                } else {
                    // GAP-05: emit visible error for null/deserialization failures (incl. Gemini thought_signature)
                    log.warn("[{}][STEP 5/7] LLM retornou resposta nula/erro", provider);
                    sessionStore.emit(ctx.requestId(), "error", Map.of("provider", provider,
                        "message", "O modelo não gerou uma resposta. Tente novamente ou use outro provider."));
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
            // T4: ollama usa RAG enxuto (topK menor + filtro de categorias);
            // anthropic/openai seguem com topK padrão sem filtro
            Map<String, Object> searchRequest = "ollama".equals(provider)
                ? Map.of("query", message, "topK", ollamaRagTopK, "categories", ollamaRagCategories)
                : Map.of("query", message, "topK", ragTopK);
            List<Map<String, Object>> chunks = aiDataClient.searchVectors(searchRequest);
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

    /**
     * T5: monta o histórico entregue a UM provider a partir dos turnos crus da sessão.
     *
     * Formato por turno:
     *   [Turno N]
     *   Usuário: {pergunta}
     *   Assistente: {resposta DESTE provider}
     *
     * Regras:
     * - Turno sem resposta (não-vazia) do provider é PULADO POR INTEIRO — inclusive
     *   o turno corrente, criado antes do fan-out e ainda sem respostas. Manter só a
     *   pergunta criaria um diálogo assimétrico que confunde modelos pequenos.
     * - Limites (truncamento por turno inteiro, do mais antigo para trás):
     *   anthropic/openai → concierge.history.max-chars (default 3000);
     *   ollama → concierge.history.ollama-max-turns (2) E ollama-max-chars (1200).
     * - Seleciona os turnos MAIS RECENTES que cabem, preservando ordem cronológica.
     */
    String buildHistoryForProvider(String provider, List<Map<String, Object>> turns) {
        if (turns == null || turns.isEmpty()) return "";

        boolean isOllama = "ollama".equals(provider);
        int maxTurns = isOllama ? ollamaHistoryMaxTurns : Integer.MAX_VALUE;
        int maxChars = isOllama ? ollamaHistoryMaxChars : historyMaxChars;

        Deque<String> selected = new ArrayDeque<>();
        int totalChars = 0;
        for (int i = turns.size() - 1; i >= 0 && selected.size() < maxTurns; i--) {
            String block = formatTurnFor(provider, turns.get(i));
            if (block == null) continue; // turno sem resposta deste provider → pulado inteiro
            int cost = block.length() + (selected.isEmpty() ? 0 : 2); // +2 = separador "\n\n"
            if (totalChars + cost > maxChars) break; // não cabe inteiro → descarta este e os anteriores
            selected.addFirst(block);
            totalChars += cost;
        }
        return String.join("\n\n", selected);
    }

    /** Bloco formatado de um turno para o provider, ou {@code null} se ele não respondeu. */
    private String formatTurnFor(String provider, Map<String, Object> turn) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> responses =
            (List<Map<String, Object>>) turn.getOrDefault("responses", List.of());
        String answer = responses.stream()
            .filter(r -> provider.equals(r.get("provider")))
            .map(r -> r.get("responseText") instanceof String s ? s : null)
            .filter(t -> t != null && !t.isBlank())
            .findFirst()
            .orElse(null);
        if (answer == null) return null;
        return "[Turno " + turn.get("turnNumber") + "]\n"
            + "Usuário: " + turn.getOrDefault("question", "") + "\n"
            + "Assistente: " + answer;
    }

    /**
     * T6: bloco de few-shot com exemplos top-rated (rating >= 4) do ai-data.
     *
     * Regras:
     * - SÓ o ollama recebe few-shot — anthropic/openai retornam "" sem sequer
     *   consultar o endpoint (economia de chamada + prompt limpo).
     * - Desabilitável via {@code concierge.fewshot.enabled} (default true);
     *   limite de exemplos via {@code concierge.fewshot.limit} (default 2).
     * - Orçamento de tokens: cada resposta é truncada em
     *   {@code concierge.fewshot.max-response-chars} (default 300) com "…" —
     *   os exemplos disputam a janela do llama3.2 3B com o histórico (1200 chars).
     * - Fail-safe total (padrão safeXxx): falha/timeout → "" + log warn;
     *   lista vazia → "" + log debug. O prompt segue sem exemplos.
     *
     * Formato:
     * <pre>
     * [Exemplos de boas respostas]
     * Usuário: {message}
     * Assistente: {response}
     *
     * Usuário: {message}
     * Assistente: {response}
     * </pre>
     */
    String safeBuildFewShotExamples(String provider) {
        if (!fewshotEnabled || !"ollama".equals(provider)) return "";
        try {
            List<Map<String, Object>> examples = aiDataClient.getTopTrainingExamples(provider, fewshotLimit);
            if (examples == null || examples.isEmpty()) {
                log.debug("[{}] few-shot: nenhum exemplo top-rated disponível", provider);
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> example : examples) {
                String question = example.get("message")  instanceof String s ? s : "";
                String answer   = example.get("response") instanceof String s ? s : "";
                if (question.isBlank() || answer.isBlank()) continue;
                if (answer.length() > fewshotMaxResponseChars) {
                    answer = answer.substring(0, fewshotMaxResponseChars) + "…";
                }
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("Usuário: ").append(question).append("\nAssistente: ").append(answer);
            }
            if (sb.length() == 0) {
                log.debug("[{}] few-shot: exemplos retornados sem message/response válidos", provider);
                return "";
            }
            log.info("[{}] few-shot: {} exemplo(s) top-rated injetado(s) no prompt", provider, examples.size());
            return "[Exemplos de boas respostas]\n" + sb;
        } catch (Exception e) {
            log.warn("[{}] few-shot indisponível — seguindo sem exemplos: {}", provider, e.getMessage());
            return "";
        }
    }

    private String buildFullMessage(String message, String contextHistory, String ragContext, String fewShotExamples) {
        StringBuilder sb = new StringBuilder();
        if (!fewShotExamples.isBlank()) {
            // T6: exemplos entram ANTES do histórico — few-shot funciona melhor
            // como "moldura" no topo do prompt (e o histórico fica colado à pergunta)
            sb.append(fewShotExamples).append("\n\n");
        }
        if (!contextHistory.isBlank()) {
            // T5: histórico já chega por provider e limitado por turno inteiro
            // (buildHistoryForProvider) — sem truncamento cego aqui
            sb.append("[Histórico da conversa]:\n").append(contextHistory).append("\n\n");
        }
        if (!ragContext.isBlank()) {
            sb.append("[Contexto do hotel]:\n").append(ragContext).append("\n\n");
        }
        sb.append(message);
        return sb.toString();
    }

    private ChatResponse callLlm(String provider, String fullMessage) {
        return callLlmWithRetry(provider, fullMessage, 1);
    }

    private ChatResponse callLlmWithRetry(String provider, String fullMessage, int retriesLeft) {
        try {
            var spec = resolveClient(provider).prompt()
                .user(fullMessage)
                .options(buildOptions(provider, resolveTemperature(provider)));
            if (shouldUseTools(provider)) {
                spec = spec.tools(hotelTools);
            }
            return spec.call().chatResponse();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            // GAP-05: Gemini thinking model emits non-standard finishReason which Spring AI can't deserialize
            if (msg.contains("MALFORMED_FUNCTION_CALL") || msg.contains("function_call_filter")) {
                log.warn("[{}] Gemini thought_signature incompatibility — emitindo erro para o front", provider);
                return null;
            }
            if (msg.contains("429") && retriesLeft > 0) {
                log.warn("[{}] 429 rate limit — aguardando 5s e retentando (restantes: {})", provider, retriesLeft);
                try { Thread.sleep(5_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                return callLlmWithRetry(provider, fullMessage, retriesLeft - 1);
            }
            log.warn("[{}] chamada LLM falhou: {}", provider, msg);
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

    /**
     * Chave de cache exato: provider + ":" + SHA-256 hex da mensagem normalizada
     * (trim + lowercase). Substitui Math.abs(hashCode()): hashCode colide e
     * Math.abs(Integer.MIN_VALUE) é negativo.
     */
    static String cacheKey(String provider, String message) {
        // Locale.ROOT: evita divergência de chave em locales como tr-TR ("I" → "ı")
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return provider + ":" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private Map<String, Object> safeGetCache(String key) {
        try { return aiDataClient.getCache(key); } catch (Exception e) { return null; }
    }

    /**
     * Lookup semântico tolerante a falha: 404 (FeignException.NotFound) é miss
     * esperado; qualquer outra exceção (timeout, conexão) também vira miss.
     */
    private Map<String, Object> safeSemanticLookup(String provider, String message) {
        try {
            return aiDataClient.semanticCacheLookup(Map.of("provider", provider, "message", message));
        } catch (FeignException.NotFound e) {
            log.debug("[{}] cache semântico: miss (404)", provider);
            return null;
        } catch (Exception e) {
            log.warn("[{}] cache semântico indisponível — tratando como miss: {}", provider, e.getMessage());
            return null;
        }
    }

    private void safePutCache(String key, String response, String provider) {
        try {
            aiDataClient.putCache(key, Map.of("response", response, "provider", provider, "ttlSeconds", 172800));
        } catch (Exception e) {
            log.debug("[{}] cache put falhou: {}", provider, e.getMessage());
        }
    }

    /** Grava no cache semântico com o mesmo TTL do exato (48h). Falha não propaga. */
    void safePutSemanticCache(String provider, String message, String response) {
        try {
            aiDataClient.putSemanticCache(Map.of(
                "provider", provider,
                "message", message,
                "response", response,
                "ttlSeconds", 172800
            ));
        } catch (Exception e) {
            log.warn("[{}] cache semântico put falhou: {}", provider, e.getMessage());
        }
    }

    private void safeTrainingSave(String provider, String message, String response) {
        if (response == null || response.isBlank()) return;
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

    // openai (Gemini via OpenAI-compat) não suporta thought_signature em tool calls multi-turn;
    // ollama (llama3.2 3B) não comporta os schemas das 10 tools → apenas anthropic recebe tools
    boolean shouldUseTools(String provider) {
        return "anthropic".equals(provider);
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
            default       -> OpenAiChatOptions.builder().temperature(temperature).build();
        };
    }
}
