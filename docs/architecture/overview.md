# Hotel Concierge AI — Visão Geral da Arquitetura

> Versão: 2.0 · Owner: Thiago · Atualizado: 2026-07-11

## 1. Serviços

| Serviço | Projeto | Porta | Responsabilidade |
|---|---|---|---|
| `front` | `front/hotel-concierge-dashboard.html` | — | Demo: exibe todos os eventos SSE por coluna (provider) em tempo real |
| `ms-hotel-concierge-ai` | `ms-hotel-concierge-ai/` | **8080** | Orquestrador + AI: recebe pergunta, chama 3 LLMs em paralelo, emite SSE |
| `ms-hotel-info` | `ms-hotel-info/` (a criar) | **8081** | Dados hoteleiros: quartos, hóspedes, disponibilidade, reservas — PostgreSQL |
| `ms-ai-data` | `ms-ai-data/` (a criar) | **8082** | AI data: embeddings/RAG (Redis Vector Store), cache de respostas, training data |

## 2. Fluxo completo

```
┌─────────────────────────────────────────────────────────────────────┐
│  [Front HTML]                                                       │
│   • input: "quero reservar spa às 18h"                              │
│   • exibe 3 colunas: Anthropic / OpenAI / Ollama                    │
└────────────────────┬────────────────────────────────────────────────┘
                     │  POST /api/v1/concierge/ask
                     │  ← 202 { requestId }
                     │  GET  /api/v1/concierge/stream/{requestId} (SSE)
                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│  ms-hotel-concierge-ai :8080  (orquestrador + AI)                  │
│                                                                     │
│  1. Feign → ms-ai-data /vectors/search   [evento: rag_search]      │
│  2. Spring AI → Anthropic / OpenAI / Ollama  (paralelo)            │
│     • token por token  [evento: token]                              │
│     • ao decidir chamar tool  [evento: tool_call]                   │
│       └─ Feign → ms-hotel-info /api/v1/...  [evento: tool_result]  │
│     • ao usar cache  [evento: cache_hit]                            │
│  3. Ao finalizar  [evento: metrics]  [evento: done]                 │
│  4. Em erro  [evento: error]                                        │
└──────────┬──────────────────────────────┬───────────────────────────┘
           │ Feign                        │ Feign
           ▼                              ▼
┌──────────────────────┐    ┌──────────────────────────────────────────┐
│  ms-hotel-info :8081 │    │  ms-ai-data :8082                        │
│  PostgreSQL 16       │    │  Redis Stack                              │
│  • Guest             │    │  • Vector Store (embeddings/RAG)          │
│  • Room              │    │  • Cache de respostas (TTL)               │
│  • ServiceType       │    │  • Training examples (dataset)            │
│  • ServiceBooking    │    │  • Indexação de documentos (admin)        │
│  • RoomBooking       │    └──────────────────────────────────────────┘
└──────────────────────┘
```

## 3. Eventos SSE — todos os eventos visíveis no front

O front renderiza **cada evento abaixo em tempo real**, por coluna (provider).

| Evento | Dados | Visível no front |
|---|---|---|
| `rag_search` | `provider`, `query`, `chunksFound`, `chunks[]` | Badge RAG + log: "vector_search(query, topK=3)" |
| `cache_hit` | `provider`, `cacheKey` | Log: "cache HIT — resposta em cache" |
| `tool_call` | `provider`, `tool`, `args`, `pendingActionId?` | Badge tool + log: "tool_call: create_booking(spa, 18:00)" |
| `tool_result` | `provider`, `tool`, `result` | Log: "tool_result: { available: true }" |
| `token` | `provider`, `chunk` | Texto da resposta aparece progressivamente |
| `metrics` | `provider`, `tokensIn`, `tokensOut`, `temperature`, `ragUsed`, `toolsUsed[]` | Linha de métricas (tokens in/out, temperatura) |
| `done` | `provider` | Remove cursor piscante, finaliza coluna |
| `error` | `provider`, `message` | Coluna em estado de erro / offline |

> `pendingActionId` presente em `tool_call` de `create_booking` — o front deve esperar o hóspede confirmar antes de o orchestrator chamar `POST /api/v1/concierge/confirm-booking`.

## 4. Tools (executadas pelo orchestrator via Feign → ms-hotel-info)

| Tool | Endpoint ms-hotel-info | Evento gerado |
|---|---|---|
| `check_availability` | `GET /api/v1/rooms/{type}/availability` | `tool_call` + `tool_result` |
| `get_price` | `GET /api/v1/pricing` | `tool_call` + `tool_result` |
| `create_booking` | `POST /api/v1/bookings` (após confirm) | `tool_call` + `tool_result` |
| `get_guest_profile` | `GET /api/v1/guests/{id}` | `tool_call` + `tool_result` |
| `search_local_attractions` | dados estáticos no orchestrator | `tool_call` + `tool_result` |

## 5. Stack técnica

| Camada | Tecnologia |
|---|---|
| Front | HTML5/CSS3/JS puro, single file, EventSource SSE |
| Orchestrator | Spring Boot 3.5, Spring AI 1.1.8, Spring WebMVC (SSE via SseEmitter), OpenFeign |
| AI providers | Anthropic Claude Sonnet, OpenAI GPT-4o-mini, Ollama llama3.2 |
| AI data | Spring Boot 3.5, Spring AI Redis Vector Store, Redis Stack |
| Hotel data | Spring Boot 3.5, Spring Data JPA, PostgreSQL 16, Flyway |
| Containers dev | Docker Compose: PostgreSQL + Redis Stack + Ollama |
| Testes | Testcontainers (Ollama, PostgreSQL, Redis Stack) |

## 6. Contratos Swagger existentes

| Arquivo | Serviço |
|---|---|
| `docs/swagger/ms-hotel-concierge-orchestrator-openapi.yaml` | ms-hotel-concierge-ai (8080) |
| `docs/swagger/ms-hotel-info-openapi.yaml` | ms-hotel-info (8081) |
| `docs/swagger/ms-ai-data-openapi.yaml` | ms-ai-data (8082) |

## 7. Módulos do repositório

```
aiproject/
├── front/
│   └── hotel-concierge-dashboard.html      # front aprovado
├── ms-hotel-concierge-ai/                  # orchestrator + AI (scaffold pronto)
├── ms-hotel-info/                          # dados do hotel (a criar)
├── ms-ai-data/                             # AI data: RAG + cache (a criar)
└── docs/
    ├── architecture/overview.md            # este arquivo
    ├── swagger/
    │   ├── ms-hotel-concierge-orchestrator-openapi.yaml
    │   ├── ms-hotel-info-openapi.yaml
    │   └── ms-ai-data-openapi.yaml
    └── specs/
        ├── hotel-concierge-ai-frontend-spec.md
        ├── ms-hotel-concierge-ai-spec.md   # orchestrator
        ├── ms-hotel-info-spec.md           # hotel data
        └── ms-ai-data-spec.md             # AI data
```
