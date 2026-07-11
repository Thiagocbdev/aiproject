# ms-hotel-concierge-ai — Spec (Orchestrator + AI)

> Versão: 1.1 · Owner: Thiago · Atualizado: 2026-07-11
> Projeto: `ms-hotel-concierge-ai/` · Swagger: `docs/swagger/ms-hotel-concierge-orchestrator-openapi.yaml`
> pom.xml já definido — não alterar dependências sem confirmar.

## 1. Responsabilidade

Ponto de entrada único do front. Recebe **uma pergunta** e a dispara para **3 endpoints de IA com configurações diferentes** em paralelo (fan-out). Agrega os eventos de todos os providers e retransmite via SSE para o front. Também executa tool calls chamando o `ms-hotel-info` e buscando contexto RAG no `ms-ai-data`.

## 2. Stack (conforme pom.xml)

| Item | Valor |
|---|---|
| Spring Boot | 3.5 · Java 21 · Porta **8080** |
| Spring AI | 1.1.8 — Anthropic, OpenAI, Ollama starters |
| HTTP client | Spring Cloud OpenFeign (chama ms-hotel-info e ms-ai-data) |
| SSE | Spring MVC `SseEmitter` ou `ResponseBodyEmitter` |
| Lombok, Actuator, Validation | incluídos no pom |
| Testes | Testcontainers (Ollama) |

## 3. Padrão central: fan-out para 3 providers

```
POST /api/v1/concierge/ask  { message, providers[] }
         │
         ├──▶ Thread Anthropic ──▶ Spring AI ChatClient (claude-sonnet-4-6, temp 0.15)
         ├──▶ Thread OpenAI    ──▶ Spring AI ChatClient (gpt-4o-mini, temp 0.35)
         └──▶ Thread Ollama    ──▶ Spring AI ChatClient (llama3.2, temp 0.80)

Cada thread emite eventos SSE com seu "provider" tag.
Todos os eventos de todos os providers fluem pelo mesmo stream SSE para o front.
```

## 4. Fluxo por provider (executado em paralelo via ExecutorService)

```
1. Feign → ms-ai-data POST /api/v1/vectors/search { query, topK:3 }
   └── emite: event: rag_search  { provider, query, chunksFound, chunks[] }

2. Verifica cache: Feign → ms-ai-data GET /api/v1/cache/{key}
   └── se HIT: emite event: cache_hit { provider, cacheKey }
             retorna resposta cacheada, pula chamada ao LLM

3. Monta prompt: system + chunks RAG + histórico de sessão + mensagem do hóspede

4. Chama ChatClient com tools registrados (streaming)
   └── a cada chunk de texto: emite event: token { provider, chunk }
   └── quando modelo decide chamar tool:
       a. emite event: tool_call { provider, tool, args, pendingActionId? }
       b. Feign → ms-hotel-info (endpoint da tool)
       c. emite event: tool_result { provider, tool, result }
       d. resultado retorna ao modelo como ToolResponse
       e. modelo continua gerando → mais eventos token

5. Ao concluir:
   └── emite event: metrics { provider, tokensIn, tokensOut, temperature, ragUsed, toolsUsed[] }
   └── emite event: done    { provider }
   └── Feign → ms-ai-data PUT /api/v1/cache/{key} (grava resposta completa)
   └── Feign → ms-ai-data POST /api/v1/training/examples (registra conversa)

6. Em exceção (ex. Ollama offline):
   └── emite event: error { provider, message }
```

## 5. Configuração dos 3 endpoints de IA

| Provider | Modelo | Temperatura | Perfil de uso |
|---|---|---|---|
| Anthropic | `claude-sonnet-4-6` | 0.15 | Booking — preciso e conservador |
| OpenAI | `gpt-4o-mini` | 0.35 | FAQ — equilibrado, traz contexto RAG |
| Ollama | `llama3.2` | 0.80 | Recommendation — criativo, fallback local |

Configurado em `application.yml`. Se Ollama offline → emite `event: error`, não quebra o stream dos outros.

## 6. Tools registrados no ChatClient

```java
// HotelTools.java — métodos anotados com @Tool do Spring AI

@Tool(description = "Verifica disponibilidade de um serviço do hotel (spa, restaurante, academia)")
AvailabilityResponse checkAvailability(String service, String date, String time) {
    return hotelInfoClient.getAvailability(service, date, time);
}

@Tool(description = "Retorna o preço de um quarto ou serviço do hotel")
PriceResponse getPrice(String itemType, String date) {
    return hotelInfoClient.getPrice(itemType, date);
}

@Tool(description = "Cria uma reserva de serviço para o hóspede — requer confirmação prévia")
BookingResponse createBooking(String guestId, String service, String date, String time) {
    return hotelInfoClient.createBooking(guestId, service, date, time);
}

@Tool(description = "Retorna o perfil e preferências do hóspede")
GuestResponse getGuestProfile(String guestId) {
    return hotelInfoClient.getGuest(guestId);
}

@Tool(description = "Informações sobre atrações locais próximas ao hotel")
String searchLocalAttractions(String query) {
    return attractionsData.search(query); // dados estáticos
}
```

## 7. Confirmação de booking (segurança de negócio)

`create_booking` **nunca** é executado automaticamente. Quando o modelo decide chamá-la:
1. Orchestrator emite `event: tool_call` com `pendingActionId`
2. Front aguarda hóspede clicar "Confirmar"
3. Front chama `POST /api/v1/concierge/confirm-booking { pendingActionId, confirm: true }`
4. Orchestrator então executa o Feign para `ms-hotel-info POST /api/v1/bookings`

## 8. Endpoints expostos

```
POST /api/v1/concierge/ask              → 202 { requestId, streamUrl }
GET  /api/v1/concierge/stream/{id}      → SSE stream
POST /api/v1/concierge/confirm-booking  → 200 { bookingId, status }
GET  /api/v1/providers                  → status dos 3 providers
GET  /api/v1/providers/temperature-profiles
GET  /actuator/health
```

## 9. Estrutura de pacotes

```
com.thiago.hotelconcierge
├── controller
│   ├── ConciergeController.java       # ask, stream, confirm-booking
│   └── ProviderController.java        # /providers, /temperature-profiles
├── service
│   ├── ConciergeService.java          # fan-out: dispara os 3 providers em paralelo
│   ├── ProviderService.java           # fluxo completo de 1 provider (RAG+chat+tools)
│   └── SessionContextService.java     # gerencia histórico de sessão por sessionId
├── tools
│   ├── HotelTools.java                # @Tool methods
│   └── AttractionData.java            # dados estáticos de atrações locais
├── client
│   ├── HotelInfoClient.java           # Feign → ms-hotel-info :8081
│   └── AiDataClient.java              # Feign → ms-ai-data :8082
├── config
│   ├── AiConfig.java                  # 3 ChatClient beans (anthropic/openai/ollama)
│   ├── FeignConfig.java
│   ├── SseConfig.java                 # timeout, keep-alive
│   └── CorsConfig.java                # libera localhost para o front
├── model
│   ├── AskRequest.java
│   ├── AskAccepted.java
│   ├── SseEvent.java
│   └── ProviderConfig.java
└── event
    └── SseEventEmitter.java           # helper para emitir eventos tipados no SseEmitter
```

## 10. application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: ms-hotel-concierge-ai
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat.options:
        model: claude-sonnet-4-6
        temperature: 0.15
    openai:
      api-key: ${OPENAI_API_KEY}
      chat.options:
        model: gpt-4o-mini
        temperature: 0.35
    ollama:
      base-url: http://localhost:11434
      chat.options:
        model: llama3.2
        temperature: 0.80

concierge:
  hotel-info-url: http://localhost:8081
  ai-data-url:    http://localhost:8082
  rag:
    top-k: 3
  session:
    ttl-minutes: 30
  ollama:
    fail-fast: false      # se offline → event:error, não quebra o stream
```
