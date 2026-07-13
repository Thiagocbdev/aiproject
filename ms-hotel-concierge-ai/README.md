# ms-hotel-concierge-ai

Orquestrador de IA — recebe uma pergunta e dispara para 3 LLMs em paralelo via SSE fan-out. Coordena RAG, tool calling e cache. Ponto de entrada único para o frontend.

**Porta:** 8080 | **Stack:** Spring Boot 3.5 · Java 21 · Spring AI 1.1.8 · Spring Cloud OpenFeign · SseEmitter

---

## Providers LLM

| Slot | Modelo | Temperatura | Papel | Tools | RAG |
|------|--------|-------------|-------|-------|-----|
| `anthropic` | OpenRouter · Nemotron 120B | 0.15 | Reservas e precisão | ✅ | ✅ |
| `openai` | Gemini 3.1 Flash Lite | 0.35 | FAQ e documentos | ❌* | ✅ |
| `ollama` | llama3.2 (local) | 0.80 | Recomendações criativas | ❌ | ❌ |

> *Gemini desactivado para tools: o modelo de pensamento gera `thoughtSignature` em tool calls multi-turn que o endpoint OpenAI-compat não propaga → compatibilidade RAG-only.

---

## Endpoints

### Health
| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/actuator/health` | Estado do serviço |

### Concierge `/api/v1/concierge`

#### POST `/api/v1/concierge/ask`
Dispara a pergunta para os providers seleccionados em paralelo. Retorna imediatamente um `requestId`.

```json
// Request body
{
  "message": "que horas abre o spa?",
  "providers": ["anthropic", "openai", "ollama"],
  "sessionId": "sess-abc123",
  "useContext": false
}
```
```json
// Response 202
{
  "requestId": "uuid",
  "streamUrl": "/api/v1/concierge/stream/uuid"
}
```
- `providers`: omitir para usar os 3 por defeito
- `sessionId`: omitir para gerar automaticamente (mesma sessão = contexto de conversa persistido)
- `useContext`: `true` inclui histórico das últimas mensagens da sessão no prompt

#### GET `/api/v1/concierge/stream/{requestId}`
SSE stream — ligar logo após o `/ask`. Produz `text/event-stream`.

**Eventos SSE por provider:**

| Evento | Dados | Significado |
|--------|-------|-------------|
| `rag_search` | `{provider, query, chunksFound, chunks[]}` | RAG buscou contexto |
| `cache_hit` | `{provider}` | Resposta vinda do cache (sem chamar LLM) |
| `tool_call` | `{provider, tool, args, pendingActionId?}` | IA chamou uma ferramenta |
| `tool_result` | `{provider, tool, result}` | Resultado da ferramenta |
| `token` | `{provider, chunk}` | Fragmento de texto da resposta |
| `metrics` | `{provider, tokensIn, tokensOut, temperature, ragUsed, cacheHit, durationMs}` | Métricas finais |
| `done` | `{provider}` | Provider terminou |
| `error` | `{provider, message}` | Erro (timeout 60s, modelo indisponível, etc.) |

#### POST `/api/v1/concierge/confirm-booking`
Confirma ou cancela uma reserva pendente criada pela IA.

```json
{ "pendingActionId": "uuid-do-tool_call", "confirm": true }
```
O `pendingActionId` vem no evento `tool_call` quando a IA chama `createBooking`.

### Providers `/api/v1/providers`
| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/api/v1/providers` | Lista status online/offline de cada provider |
| GET | `/api/v1/providers/temperature-profiles` | Temperaturas configuradas (booking=0.15, faq=0.35, recommendation=0.80) |

---

## Tools (Function Calling)

Disponíveis para os providers com tools activas (`anthropic`):

| Tool | Parâmetros | Descrição |
|------|-----------|-----------|
| `searchGuests` | `name` | Busca hóspedes por nome; retorna UUID para usar noutras tools |
| `getGuestProfile` | `guestId` (UUID) | Perfil completo — usar UUID retornado por `searchGuests` |
| `createGuest` | `name`, `email`, `phone?` | Cadastra novo hóspede |
| `listRooms` | `type?` (STANDARD/DELUXE/SUITE) | Lista quartos disponíveis |
| `checkAvailability` | `serviceType`, `date` (YYYY-MM-DD), `time` (HH:mm) | Verifica disponibilidade de serviço |
| `getPrice` | `serviceType`, `date` (YYYY-MM-DD) | Preço de um serviço |
| `createBooking` | `guestId`, `serviceType`, `date`, `time` | Prepara reserva (requer confirmação humana) |
| `getGuestBookings` | `guestId` | Histórico de reservas do hóspede |
| `getBooking` | `bookingId` | Detalhes de uma reserva |
| `cancelBooking` | `bookingId` | Cancela reserva (multa 100% se < 48h) |
| `searchLocalAttractions` | `query` | Atracções locais, restaurantes, actividades |

> `serviceType` aceita: `spa`, `restaurant`, `gym`, `room_service`

---

## Fluxo de pedido

```
POST /ask → fanOut() com CountDownLatch (timeout 60s)
                ├── anthropic: RAG + Nemotron 120B + tools → SSE events
                ├── openai:    RAG + Gemini Flash Lite (RAG only) → SSE events
                └── ollama:    cache check + llama3.2 (local) → SSE events
GET /stream/{id} ← EventSource SSE (frontend ligado)
```

Após 60s, qualquer provider que não tenha respondido recebe evento `error` de timeout e o SSE fecha.

---

## Correr localmente

O MS carrega o ficheiro `.env` da raiz do projecto automaticamente na startup (sem precisar de variáveis de ambiente):

```bash
# Infra em Docker
docker compose up -d postgres redis

# Ollama a correr nativamente
ollama serve   # se não estiver já a correr

# Iniciar o MS (carrega .env automaticamente)
.\run-local.ps1 concierge
# ou
cd ms-hotel-concierge-ai && mvn spring-boot:run
```

**Variáveis de ambiente** (lidas do `.env` ou do sistema):
```
OPENROUTER_API_KEY=sk-or-v1-...    # provider anthropic (Nemotron 120B)
GEMINI_API_KEY=AIza...             # provider openai (Gemini Flash Lite)
HOTEL_INFO_URL=http://localhost:8081  # default — OK em modo local
AI_DATA_URL=http://localhost:8082     # default — OK em modo local
OLLAMA_BASE_URL=http://localhost:11434 # default — OK em modo local
```

---

## Docker

```bash
docker compose up -d ms-hotel-concierge-ai
```

Em Docker, as env vars vêm do `docker-compose.yml`. O `.env` não é copiado para o container (gitignored), por isso o `DotEnvPostProcessor` não faz nada — as chaves vêm das variáveis Docker.
