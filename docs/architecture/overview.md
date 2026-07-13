# Hotel Concierge AI — Visão Geral da Arquitetura

> Versão: 3.0 · Owner: Thiago · Atualizado: 2026-07-13

## 1. Serviços

| Serviço | Pasta | Porta | Responsabilidade |
|---------|-------|-------|-----------------|
| `front` | `front/hotel-concierge-dashboard.html` | — | Dashboard SSE: 3 colunas em tempo real por provider |
| `ms-hotel-concierge-ai` | `ms-hotel-concierge-ai/` | **8080** | Orquestrador + IA: fan-out para 3 LLMs, RAG, tool calling, SSE |
| `ms-hotel-info` | `ms-hotel-info/` | **8081** | Dados hoteleiros: hóspedes, quartos, reservas, preços (PostgreSQL) |
| `ms-ai-data` | `ms-ai-data/` | **8082** | IA data: RAG (Redis Vector Store), cache de respostas (TTL 48h), training |

## 2. Fluxo completo

```
┌─────────────────────────────────────────────────────────────────────┐
│  [Front HTML]                                                       │
│   • input: "quero reservar spa às 18h"                              │
│   • exibe 3 colunas: Nemotron / Gemini / Llama                      │
└────────────────────┬────────────────────────────────────────────────┘
                     │  POST /api/v1/concierge/ask
                     │  ← 202 { requestId }
                     │  GET  /api/v1/concierge/stream/{requestId} (SSE)
                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│  ms-hotel-concierge-ai :8080  (orquestrador + IA)                  │
│                                                                     │
│  fanOut() — CountDownLatch, timeout 60s, Virtual Threads            │
│                                                                     │
│  ┌─ anthropic ─────────────────────────────────────────────────┐    │
│  │  RAG → Nemotron 120B (OpenRouter) + tools  → tokens SSE     │    │
│  └─────────────────────────────────────────────────────────────┘    │
│  ┌─ openai ────────────────────────────────────────────────────┐    │
│  │  RAG → Gemini Flash Lite (RAG only, sem tools) → tokens SSE │    │
│  └─────────────────────────────────────────────────────────────┘    │
│  ┌─ ollama ────────────────────────────────────────────────────┐    │
│  │  cache check → llama3.2 local → tokens SSE                  │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  Ao finalizar provider:  [evento: metrics]  [evento: done]          │
│  Ao expirar 60s:         [evento: error]  sessionStore.complete()   │
└──────────┬──────────────────────────────┬───────────────────────────┘
           │ Feign                        │ Feign
           ▼                              ▼
┌──────────────────────┐    ┌──────────────────────────────────────────┐
│  ms-hotel-info :8081 │    │  ms-ai-data :8082                        │
│  PostgreSQL 16       │    │  Redis Stack                              │
│  • Guest             │    │  • Vector Store (nomic-embed-text)        │
│  • Room              │    │  • Cache de respostas (TTL 48h)           │
│  • ServiceType       │    │  • Training examples (dataset)            │
│  • ServiceBooking    │    └──────────────────────────────────────────┘
└──────────────────────┘
```

## 3. Eventos SSE — todos os eventos visíveis no front

O front renderiza **cada evento abaixo em tempo real**, por coluna (provider).

| Evento | Dados | Visível no front |
|--------|-------|-----------------|
| `rag_search` | `provider`, `query`, `chunksFound`, `chunks[]` | Badge RAG + log: "vector_search(query, topK=3)" |
| `cache_hit` | `provider` | Log: "cache HIT — resposta em cache" |
| `tool_call` | `provider`, `tool`, `args`, `pendingActionId?` | Badge tool + log: "tool_call: checkAvailability(spa, 18:00)" |
| `tool_result` | `provider`, `tool`, `result` | Log: "tool_result: { available: true }" |
| `token` | `provider`, `chunk` | Texto da resposta aparece progressivamente |
| `metrics` | `provider`, `tokensIn`, `tokensOut`, `temperature`, `ragUsed`, `cacheHit`, `durationMs` | Linha de métricas |
| `done` | `provider` | Remove cursor piscante, finaliza coluna |
| `error` | `provider`, `message` | Coluna em estado de erro (timeout, modelo indisponível) |

> `pendingActionId` presente em `tool_call` de `createBooking` — o front deve aguardar confirmação do hóspede antes de o orchestrator chamar `POST /api/v1/concierge/confirm-booking`.

## 4. Providers LLM

| Slot | Modelo | Endpoint | Tools | RAG | Temp |
|------|--------|----------|-------|-----|------|
| `anthropic` | Nemotron 120B | OpenRouter (`openrouter.ai/api/v1`) | ✅ | ✅ | 0.15 |
| `openai` | Gemini 3.1 Flash Lite | Google AI Studio (OpenAI-compat) | ❌* | ✅ | 0.35 |
| `ollama` | llama3.2 | Nativo Windows (`localhost:11434`) | ❌ | ❌ | 0.80 |

> *Gemini desactivado para tools: é um modelo de pensamento (thinking model) que gera `thoughtSignature` em respostas de tool calls multi-turn; o endpoint OpenAI-compatível não propaga esse campo nas chamadas seguintes → erro 400 MALFORMED_FUNCTION_CALL. Solução: Gemini RAG-only.

## 5. Tools (function calling — só provider `anthropic`)

As tools são Spring AI `@Tool` methods em `HotelTools.java`. Cada chamada gera eventos `tool_call` + `tool_result` no SSE.

| Tool | Endpoint ms-hotel-info chamado |
|------|-------------------------------|
| `searchGuests` | `GET /api/v1/guests?search=` |
| `getGuestProfile` | `GET /api/v1/guests/{guestId}` |
| `createGuest` | `POST /api/v1/guests` |
| `listRooms` | `GET /api/v1/rooms` |
| `checkAvailability` | `GET /api/v1/rooms/{serviceType}/availability` |
| `getPrice` | `GET /api/v1/pricing?serviceType=` |
| `createBooking` | `POST /api/v1/bookings` (após confirm) |
| `getGuestBookings` | `GET /api/v1/bookings?guestId=` |
| `getBooking` | `GET /api/v1/bookings/{bookingId}` |
| `cancelBooking` | `PATCH /api/v1/bookings/{bookingId}` → `CANCELLED` |
| `searchLocalAttractions` | dados estáticos no orchestrator |

## 6. Cache (Redis TTL 48h)

Chave: `{provider}:{Math.abs(message.hashCode())}`

- **Cache hit**: emite evento `cache_hit`, não chama LLM — resposta instantânea
- **Cache miss**: chama LLM, guarda resposta no Redis com TTL 172800s
- Implementado em `ProviderOrchestrator.java` antes e depois da chamada LLM
- `ms-ai-data` expõe `GET/PUT /api/v1/cache/{key}` usados via Feign

## 7. Stack técnica

| Camada | Tecnologia |
|--------|-----------|
| Front | HTML5/CSS3/JS puro, single file, EventSource SSE |
| Orchestrator | Spring Boot 3.5, Spring AI 1.1.8, Spring WebMVC (SseEmitter), Spring Cloud OpenFeign |
| AI providers | OpenRouter Nemotron 120B · Gemini 3.1 Flash Lite · Ollama llama3.2 |
| Embeddings | nomic-embed-text via Ollama (768 dimensões) |
| Vector store | Redis Stack (RediSearch module) |
| Hotel data | Spring Boot 3.5, Spring Data JPA, PostgreSQL 16, Flyway |
| Cache | Redis (TTL 48h, key hash) |
| Infra dev | Docker Compose: PostgreSQL + Redis Stack (Ollama nativo Windows) |
| Execução | Java 21 Virtual Threads (`newVirtualThreadPerTaskExecutor`) |

## 8. Infraestrutura Docker

| Container | Imagem | Porta | Descrição |
|-----------|--------|-------|-----------|
| `hotel-postgres` | postgres:16-alpine | 5432 | DBs `hotel_info` e `ai_data` |
| `hotel-redis` | redis/redis-stack:latest | 6379 + 8001 | Vector store + cache + RedisInsight |
| `ms-hotel-info` | local build | 8081 | MS dados do hotel |
| `ms-ai-data` | local build | 8082 | MS dados IA |
| `ms-hotel-concierge-ai` | local build | 8080 | MS orquestrador |
| Ollama | **nativo Windows** | 11434 | llama3.2 + nomic-embed-text (não Docker) |

> **Porquê Ollama nativo?** O passthrough de GPU em Docker/Windows requer WSL2 com nvidia-container-toolkit — setup complexo e instável. Correndo nativamente o acesso à GPU é directo e o Ollama usa a extensão `host.docker.internal:11434` para ser acessível pelos containers.

## 9. Modos de execução

### Modo A — tudo Docker
```bash
cp .env.example .env   # preencher API keys
docker compose up -d
# abrir front/hotel-concierge-dashboard.html
```

### Modo B — infra Docker, MSes locais (dev)
```bash
docker compose up -d postgres redis
.\run-local.ps1 hotel-info    # porta 8081
.\run-local.ps1 ai-data       # porta 8082
.\run-local.ps1 concierge     # porta 8080 (carrega .env automaticamente)
```

Os `application.yml` de cada MS têm defaults `localhost` para todas as dependências — nenhuma var de ambiente adicional é necessária exceto as API keys do concierge, lidas do `.env` via `DotEnvPostProcessor`.

## 10. Contratos

| Ficheiro | Serviço |
|----------|---------|
| `docs/swagger/ms-hotel-concierge-orchestrator-openapi.yaml` | ms-hotel-concierge-ai (8080) |
| `docs/swagger/ms-hotel-info-openapi.yaml` | ms-hotel-info (8081) |
| `docs/swagger/ms-ai-data-openapi.yaml` | ms-ai-data (8082) |
| `docs/postman/ms-hotel-concierge-ai.postman_collection.json` | concierge |
| `docs/postman/ms-hotel-info.postman_collection.json` | hotel-info |
| `docs/postman/ms-ai-data.postman_collection.json` | ai-data |

## 11. Estrutura do repositório

```
aiproject/
├── front/
│   └── hotel-concierge-dashboard.html   ← dashboard SSE (abrir directamente no browser)
├── ms-hotel-concierge-ai/               ← orquestrador + IA (porta 8080)
├── ms-hotel-info/                       ← dados do hotel (porta 8081)
├── ms-ai-data/                          ← RAG + cache (porta 8082)
├── docs/
│   ├── architecture/overview.md         ← este ficheiro
│   ├── postman/                         ← collections Postman
│   ├── swagger/                         ← contratos OpenAPI YAML
│   └── specs/                           ← specs originais de cada MS
├── .run/                                ← run configs IntelliJ (committáveis, sem secrets)
├── run-local.ps1                        ← script PowerShell modo local
├── docker-compose.yml
├── .env.example                         ← template (sem API keys)
└── .env                                 ← gitignored — API keys aqui
```
