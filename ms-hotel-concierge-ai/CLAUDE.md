# ms-hotel-concierge-ai — Orchestrator + AI

**Porta:** 8080 | **Stack:** Spring Boot 3.5 + Spring AI 1.1.8 + Java 21

## Spec completa
`../docs/specs/ms-hotel-concierge-ai-spec.md`
`../docs/swagger/ms-hotel-concierge-orchestrator-openapi.yaml`

## O que este serviço faz

Recebe UMA pergunta do front e dispara para **3 endpoints de LLM com configs diferentes em paralelo** (fan-out):

```
POST /api/v1/concierge/ask  { message, providers[] }
         ├──▶ Anthropic claude-sonnet-4-6  (temp 0.15 — booking)
         ├──▶ OpenAI gpt-4o-mini           (temp 0.35 — FAQ + RAG)
         └──▶ Ollama llama3.2              (temp 0.80 — recommendation)

GET /api/v1/concierge/stream/{requestId}   SSE
    └── eventos por provider: rag_search, cache_hit, tool_call, tool_result, token, metrics, done, error
```

## Dependências externas (Feign)
- `ms-hotel-info :8081` — tool calls (check_availability, create_booking, get_price, get_guest)
- `ms-ai-data :8082` — RAG (POST /api/v1/vectors/search) + cache (GET/PUT /api/v1/cache/{key})

## Estrutura de pacotes a criar

```
com.thiago.hotelconcierge
├── controller/   ConciergeController, ProviderController
├── service/      ConciergeService (fan-out), ProviderService (1 provider), SessionContextService
├── tools/        HotelTools (@Tool methods), AttractionData
├── client/       HotelInfoClient (Feign :8081), AiDataClient (Feign :8082)
├── config/       AiConfig (3 ChatClient beans), FeignConfig, SseConfig, CorsConfig
├── model/        AskRequest, AskAccepted, SseEvent, ProviderConfig
└── event/        SseEventEmitter
```

## Regras de negócio críticas
- `create_booking` NUNCA executa sem `POST /api/v1/concierge/confirm-booking` explícito
- Se Ollama offline: emite `event:error { provider:"ollama" }` e NÃO quebra o stream dos outros
- Cada evento SSE deve ter campo `"provider"` para o front renderizar na coluna correta

## application.yml (resumo)
```yaml
server.port: 8080
spring.ai.anthropic.api-key: ${ANTHROPIC_API_KEY}
spring.ai.openai.api-key: ${OPENAI_API_KEY}
spring.ai.ollama.base-url: http://localhost:11434
concierge.hotel-info-url: http://localhost:8081
concierge.ai-data-url: http://localhost:8082
```
