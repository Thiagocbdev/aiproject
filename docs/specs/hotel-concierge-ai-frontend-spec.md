# ms-hotel-concierge-ai — Frontend spec (dashboard de demonstração)

> Status: rascunho (draft) · Versão: 0.1 · Owner: Thiago
> Complementa `hotel-concierge-ai-spec.md` (backend/domínio hexagonal). Este documento cobre **só o front**, que será apresentado como demo do que existe por trás (RAG, agents, multi-modelo, temperatura).

## 1. Objetivo do front

Um **dashboard de demonstração** onde uma única pergunta do "hóspede" é enviada simultaneamente para 3 providers de LLM (Anthropic, OpenAI, Ollama), e cada coluna mostra em tempo real:

- status online/offline do provider
- tokens in/out
- temperatura usada (perfil: booking / faq / recommendation)
- se RAG foi usado (e quais chunks, opcional em tooltip)
- quais tools/agents foram chamados (`check_availability`, `get_price`, `create_booking`, `search_local_attractions`)
- um "live log" estilo terminal com o que está acontecendo (tool_call, vector_search, etc.)
- as respostas do modelo aparecendo progressivamente (efeito streaming)

Isso deixa visível, numa apresentação, a arquitetura hexagonal (porta multi-modelo, porta RAG, porta de tools) descrita no spec de backend.

## 2. Fora de escopo (nesta v0.1)

- Autenticação/login
- Persistência real (tudo é mockado/local no front por enquanto)
- Chamada real a LLMs — o **backend Spring** é quem fala com os providers; o front só consome os endpoints dele

## 3. Contrato com o backend (Spring)

O front **não** chama OpenAI/Anthropic/Ollama diretamente. Ele consome o `ms-hotel-concierge-ai` (Spring) via REST/SSE. Endpoints esperados (ajustar nomes quando o backend estiver definido):

```
POST /api/concierge/ask
  body: { "message": string, "providers": ["anthropic","openai","ollama"] }
  response: { "requestId": string }

GET /api/concierge/stream/{requestId}   (Server-Sent Events)
  eventos por provider, ex:
  event: token
  data: { "provider": "anthropic", "chunk": "Encontrei..." }

  event: tool_call
  data: { "provider": "anthropic", "tool": "create_booking", "args": {...} }

  event: metrics
  data: { "provider": "anthropic", "tokensIn": 142, "tokensOut": 87, "temperature": 0.15, "ragUsed": false }

  event: done
  data: { "provider": "anthropic" }
```

> Enquanto o backend não expõe SSE, o front deve funcionar com **mock local** (dados fake com delay via `setTimeout`) atrás de uma camada de serviço (`services/conciergeService.ts`), para trocar por `fetch`/`EventSource` real depois sem tocar nos componentes.

## 4. Estrutura de componentes (React)

```
src/
├── components/
│   ├── AskBar.jsx                 # input + botão enviar
│   ├── ProviderColumn.jsx         # uma coluna (Anthropic/OpenAI/Ollama)
│   ├── ProviderStatusBadge.jsx    # bolinha online/offline
│   ├── MetricsRow.jsx             # tokens in/out, temperatura
│   ├── ToolBadge.jsx              # badge de tool/rag usado
│   ├── LiveLog.jsx                # log estilo terminal, streaming
│   └── ResponseBubble.jsx         # bolha de resposta, aparece progressivamente
├── services/
│   └── conciergeService.js        # abstração: mock agora, fetch/SSE depois
├── mocks/
│   └── conciergeMock.js           # respostas fake com delay simulando streaming
└── pages/
    └── Dashboard.jsx               # layout geral (AskBar + 3 ProviderColumn)
```

Projeto em JavaScript puro (sem TypeScript). Se quiser documentar a forma dos objetos sem tipagem forte, dá pra usar JSDoc nos comentários.

## 5. Estado por provider

```js
// forma esperada do estado de cada provider (JSDoc, sem TS)
/**
 * @typedef {Object} ProviderState
 * @property {'anthropic'|'openai'|'ollama'} id
 * @property {string} label
 * @property {boolean} online
 * @property {number} [tokensIn]
 * @property {number} [tokensOut]
 * @property {number} [temperature]
 * @property {boolean} ragUsed
 * @property {string[]} toolsUsed
 * @property {string[]} logLines
 * @property {string[]} responses
 * @property {boolean} streaming
 */
```

## 6. Design (dark mode, estilo do mockup aprovado)

- Fundo geral bem escuro, cards em um cinza um pouco mais claro (elevação sutil, sem sombra pesada)
- 3 colunas lado a lado (`grid-template-columns: repeat(3, 1fr)`), responsivo → empilha em telas estreitas
- Cada coluna: header (nome + status), linha de métricas em fonte monoespaçada, badges de tools/rag, caixa de log estilo terminal, lista de bolhas de resposta
- Cor de destaque por conceito, não por provider: badges de **tool call** e **RAG** com cores distintas e consistentes entre colunas
- Streaming: texto aparece incrementalmente (efeito "digitando"), log também recebe linhas novas em tempo real
- Sem gradientes/sombras pesadas — visual limpo, adequado pra projetar em apresentação

## 7. Roadmap do front

1. Layout estático (o que já foi prototipado) com dados mockados fixos
2. Mock com delay simulando streaming (tokens chegando aos poucos)
3. Trocar mock por `fetch` real assim que o endpoint `POST /api/concierge/ask` existir
4. Trocar polling/mock de streaming por `EventSource` (SSE) quando o backend expuser
5. Ajustes finos de design pós-integração real
