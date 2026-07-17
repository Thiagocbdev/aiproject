# ms-hotel-concierge-ai — Orchestrator + AI

**Porta:** 8080 | **Stack:** Spring Boot 3.5 · Spring AI 1.1.8 · Java 21 Virtual Threads

## Responsabilidade

Recebe uma pergunta do front e dispara para **3 endpoints de LLM em paralelo** (fan-out via Virtual Threads).
Transmite os eventos de cada provider em tempo real via SSE. Coordena RAG, cache e tool calling.

## Documentação completa

- `../docs/specs/ms-hotel-concierge-ai-spec.md`
- `../docs/swagger/ms-hotel-concierge-orchestrator-openapi.yaml`

## Fluxo principal

```
POST /api/v1/concierge/ask  { message, providers[], sessionId? }
         ├──▶ OpenRouter  claude-sonnet-4-6  (temp 0.15 — reservas e operações críticas)
         ├──▶ Gemini      gemini-2.0-flash   (temp 0.35 — FAQ + RAG)
         └──▶ Ollama      llama3.2           (temp 0.80 — sugestões e recomendações)

GET /api/v1/concierge/stream/{requestId}   text/event-stream
    └── eventos: rag_search, cache_hit, tool_call, tool_result, token, metrics, done, error
```

## Dependências externas (Feign)

- `ms-hotel-info :8081` — tool calls: check_availability, create_booking, get_price, get_guest
- `ms-ai-data :8082` — RAG (POST /vectors/search) + cache (GET/PUT /cache/{key})

## Pacotes

```
com.thiago.hotelconcierge
├── controller/   ConciergeController, ProviderController
├── service/      ConciergeService (fan-out), ProviderService (1 provider), SessionContextService
├── tools/        HotelTools (@Tool methods), AttractionData
├── client/       HotelInfoClient (Feign :8081), AiDataClient (Feign :8082)
├── config/       AiConfig (ChatClient beans por provider), FeignConfig, SseConfig, CorsConfig
├── model/        AskRequest, AskAccepted, SseEvent, ProviderConfig
└── event/        SseEventEmitter
```

## Regras de negócio críticas

- `create_booking` nunca executa automaticamente — aguarda `POST /api/v1/concierge/confirm-booking`
- Se Ollama offline: emite `event:error { provider:"ollama" }` sem quebrar o stream dos demais providers
- Cada evento SSE carrega campo `"provider"` para o front renderizar na coluna correta
- `CountDownLatch` garante que o orquestrador aguarda todos os providers antes de fechar o stream

## Tool calling disponível

| Tool | O que faz |
|---|---|
| `check_availability` | Disponibilidade de quarto/serviço em data/hora |
| `get_price` | Tarifa atual por tipo de quarto |
| `create_booking` | Cria reserva (requer confirmação explícita do hóspede) |
| `get_guest_profile` | Dados do hóspede por ID |
| `search_local_attractions` | Atrações locais com distância e preço |

## Configuração

```yaml
server.port: 8080
spring.ai.openrouter.api-key: ${OPENROUTER_API_KEY}
spring.ai.gemini.api-key: ${GEMINI_API_KEY}
spring.ai.ollama.base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
concierge.hotel-info-url: ${HOTEL_INFO_URL:http://localhost:8081}
concierge.ai-data-url: ${AI_DATA_URL:http://localhost:8082}
```

## Observação sobre Ollama e tool calling

Modelos locais pequenos (llama3.2, 3B parâmetros) não executam function calling de forma confiável.
O Ollama é posicionado para geração de texto criativo (sugestões, recomendações) onde a temperatura
alta é um benefício. Operações transacionais usam OpenRouter e Gemini.
