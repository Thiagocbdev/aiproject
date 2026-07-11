# Front — Hotel Concierge AI (spec atualizada)

> Versão: 1.2 · Owner: Thiago · Atualizado: 2026-07-11
> HTML aprovado: `front/hotel-concierge-dashboard.html` (sem alterações no layout/CSS).

## 1. Stack

Single file HTML/CSS/JS puro. Sem framework, sem build step. Decisão final.

## 2. Fluxo principal do front

```
Hóspede digita pergunta → clica "Enviar"
         │
         │  POST /api/v1/concierge/ask
         │  body: { message, providers: ["anthropic","openai","ollama"] }
         ▼
  Orchestrator retorna imediatamente:
         { "requestId": "uuid", "streamUrl": "/api/v1/concierge/stream/{uuid}" }
         │
         │  GET /api/v1/concierge/stream/{requestId}  (SSE)
         ▼
  EventSource aberto — eventos chegam tagged por provider
  Front renderiza cada evento na coluna correta em tempo real
```

> O front **nunca** chama Anthropic/OpenAI/Ollama diretamente. Toda a IA fica no orchestrator.

## 3. Mapeamento evento SSE → DOM

| Evento | Campo `provider` | Ação na coluna |
|---|---|---|
| `rag_search` | ex: `"openai"` | Badge RAG (`rag faq`) + log: `> vector_search(query, topK=3)` |
| `cache_hit` | ex: `"anthropic"` | Log: `> cache HIT` |
| `tool_call` | ex: `"anthropic"` | Badge tool (ex: `create_booking`) + log: `> tool_call: create_booking(spa, 18:00)` |
| `tool_result` | ex: `"anthropic"` | Log: `> tool_result: { available: true }` |
| `token` | ex: `"anthropic"` | Concatena `chunk` na bolha de resposta (efeito streaming) |
| `metrics` | ex: `"openai"` | Atualiza `#tok-{provider}` com `tokensIn / tokensOut` e temperatura |
| `done` | ex: `"ollama"` | Remove cursor piscante do log, marca coluna como concluída |
| `error` | ex: `"ollama"` | Log vermelho + bolinha de status → offline |

## 4. Contrato de request/response

### POST /api/v1/concierge/ask

```json
// request
{
  "message": "quero reservar o spa às 18h",
  "providers": ["anthropic", "openai", "ollama"],
  "sessionId": "guest-482"
}

// response 202
{
  "requestId": "8f14e45f-0100-4c1f-8f1a-19f7d2b3a1a4",
  "streamUrl": "/api/v1/concierge/stream/8f14e45f-0100-4c1f-8f1a-19f7d2b3a1a4"
}
```

### GET /api/v1/concierge/stream/{requestId}  — SSE

```
Content-Type: text/event-stream

event: rag_search
data: {"provider":"openai","query":"spa horários disponibilidade","chunksFound":3}

event: tool_call
data: {"provider":"anthropic","tool":"create_booking","args":{"service":"spa","time":"18:00"},"pendingActionId":"abc-123"}

event: tool_result
data: {"provider":"anthropic","tool":"create_booking","result":{"status":"pending_confirmation"}}

event: token
data: {"provider":"anthropic","chunk":"Encontrei horário "}

event: token
data: {"provider":"anthropic","chunk":"às 18h no spa. Confirma?"}

event: metrics
data: {"provider":"anthropic","tokensIn":142,"tokensOut":87,"temperature":0.15,"ragUsed":false,"toolsUsed":["create_booking"]}

event: done
data: {"provider":"anthropic"}

event: error
data: {"provider":"ollama","message":"serviço local indisponível"}
```

## 5. Integração JS (substituir mock quando backend estiver pronto)

```js
async function askConcierge(message) {
  const res = await fetch('http://localhost:8080/api/v1/concierge/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message,
      providers: ['anthropic', 'openai', 'ollama'],
      sessionId: 'guest-demo'
    })
  }).then(r => r.json());

  const es = new EventSource(
    `http://localhost:8080${res.streamUrl}`
  );

  es.addEventListener('rag_search',  e => handleRag(JSON.parse(e.data)));
  es.addEventListener('cache_hit',   e => handleCacheHit(JSON.parse(e.data)));
  es.addEventListener('tool_call',   e => handleToolCall(JSON.parse(e.data)));
  es.addEventListener('tool_result', e => handleToolResult(JSON.parse(e.data)));
  es.addEventListener('token',       e => handleToken(JSON.parse(e.data)));
  es.addEventListener('metrics',     e => handleMetrics(JSON.parse(e.data)));
  es.addEventListener('done',        e => { handleDone(JSON.parse(e.data)); checkAllDone(es); });
  es.addEventListener('error',       e => handleError(JSON.parse(e.data)));
}
```

## 6. CORS

O orchestrator deve liberar `http://localhost:*` para o front estático. Ver `CorsConfig.java` no orchestrator.

## 7. Roadmap

| Etapa | Status |
|---|---|
| Layout estático com mock inline | ✅ pronto |
| Adicionar listeners para `tool_result` e `cache_hit` no DOM | aguarda backend |
| Integração real POST /ask + EventSource SSE | aguarda backend |
| Confirmação de booking via `pendingActionId` (botão no front) | futuro |
