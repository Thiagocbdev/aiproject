# ms-hotel-concierge-ai — Spec inicial

> Status: rascunho (draft) · Versão: 0.1 · Owner: Thiago

## 1. Objetivo

Demonstrar, em um projeto real de portfólio, conhecimento aplicado em:

- **RAG** (Retrieval Augmented Generation)
- **Agents** (tool calling / function calling)
- **Controle de parâmetros de geração** (temperatura, top_p)
- **Integração multi-modelo** com múltiplos provedores e API keys
- **SDD** (Spec-Driven Development) como processo, não só o código

O caso de uso: um **concierge de hotel** que interage com o hóspede via chat (site, app ou totem), respondendo perguntas sobre o hotel, sugerindo passeios/restaurantes, e executando ações reais como consultar disponibilidade, preço e criar reservas.

## 2. Por que hexagonal

O domínio (regras de concierge: quando chamar uma ferramenta, quando responder direto, como formatar a resposta) **não pode depender** de qual provedor de LLM está sendo usado, nem de qual vector store guarda os embeddings. Isso é exatamente o motivo de existir hexagonal: IA aqui é só mais um adapter de infraestrutura.

```
┌─────────────────────────────────────────────────────────────┐
│                    ms-hotel-concierge                        │
│                                                               │
│   ┌────────────┐      ┌───────────────┐      ┌────────────┐  │
│   │  Porta de  │      │    Domínio    │      │ Porta LLM  │  │
│   │  entrada   │─────▶│  (orquestra-  │◀────▶│ (strategy  │  │
│   │ chat REST  │      │  ção, regras) │      │ multi-modelo)│ │
│   └────────────┘      └───────┬───────┘      └──────┬─────┘  │
│                                │                     │        │
│                       ┌────────▼────────┐            │        │
│                       │ Porta ferramentas│           │        │
│                       │ (tool calling)   │           │        │
│                       └────────┬────────┘            │        │
└────────────────────────────────┼─────────────────────┼────────┘
                                  │                     │
                  ┌───────────────┼──────┐   ┌──────────┼──────────┐
                  ▼               ▼      ▼   ▼          ▼          ▼
          ┌─────────────┐  ┌───────────┐   ┌────────┐┌──────────┐┌────────┐
          │  Feign:      │  │ Redis /   │   │ OpenAI ││Anthropic ││ Ollama │
          │  disponib.,  │  │ pgvector  │   │adapter ││ adapter  ││ local  │
          │  preço,      │  │ (RAG)     │   │        ││          ││adapter │
          │  reserva     │  │           │   │        ││          ││        │
          └─────────────┘  └───────────┘   └────────┘└──────────┘└────────┘
```

## 3. Estrutura de pacotes (hexagonal)

```
com.thiago.hotelconcierge
├── domain
│   ├── model                # ConciergeRequest, ConciergeResponse, HospedeContext
│   └── service               # ConciergeOrchestrator (regras de negócio puras)
├── application
│   └── usecase                # AtenderMensagemUseCase, ExecutarFerramentaUseCase
├── ports
│   ├── in
│   │   └── ChatInboundPort.java
│   └── out
│       ├── ChatModelPort.java         # abstração multi-modelo
│       ├── VectorStorePort.java       # abstração RAG
│       ├── ToolExecutorPort.java      # abstração de tool calling
│       └── BookingServicePort.java    # abstração dos serviços internos
├── adapters
│   ├── in
│   │   └── rest
│   │       └── ConciergeChatController.java
│   └── out
│       ├── llm
│       │   ├── OpenAiChatModelAdapter.java
│       │   ├── AnthropicChatModelAdapter.java
│       │   ├── OllamaChatModelAdapter.java
│       │   └── ModelRouter.java        # escolhe adapter por config/fallback
│       ├── rag
│       │   └── RedisVectorStoreAdapter.java
│       ├── tools
│       │   ├── DisponibilidadeTool.java
│       │   ├── PrecoTool.java
│       │   └── ReservaTool.java
│       └── booking
│           └── BookingFeignClient.java
└── config
    ├── LlmProviderProperties.java     # api keys, timeouts, modelo por provider
    └── TemperatureProfileProperties.java
```

## 4. RAG

**Fonte de dados**: FAQs do hotel, políticas de cancelamento, descrição de quartos, atrações locais.

**Pipeline**:
1. Ingestão offline: documentos → chunking → embeddings → `VectorStorePort.save(...)`.
2. Em runtime: mensagem do hóspede → embedding da pergunta → `VectorStorePort.search(query, topK)` → contexto injetado no prompt.

**Escolha de store**: Redis (módulo de busca vetorial) por reaproveitar infraestrutura já conhecida; pgvector como alternativa se o time já tiver Postgres gerenciado.

```java
public interface VectorStorePort {
    void index(String id, String content, Map<String, String> metadata);
    List<RetrievedChunk> search(String query, int topK);
}
```

## 5. Agents (tool calling)

O domínio expõe um conjunto de ferramentas que o modelo pode decidir chamar. Loop clássico: **pensar → chamar ferramenta → observar → responder**.

| Ferramenta | Chama | Determinístico? |
|---|---|---|
| `check_availability` | `BookingFeignClient` | sim |
| `get_price` | `BookingFeignClient` | sim |
| `create_booking` | `BookingFeignClient` (requer confirmação explícita do usuário) | sim |
| `search_local_attractions` | RAG | não (pode variar) |

```java
public interface ToolExecutorPort {
    ToolResult execute(String toolName, Map<String, Object> arguments);
}
```

Regra de negócio importante: `create_booking` **nunca** é chamada sem uma etapa de confirmação explícita do hóspede — o agente não decide reservar sozinho.

## 6. Temperatura por caso de uso

| Caso de uso | Temperatura | Motivo |
|---|---|---|
| Confirmação de reserva / cálculo de preço | 0.1 – 0.2 | precisa ser determinístico, sem "criatividade" |
| Resposta a FAQ (com RAG) | 0.3 – 0.4 | precisão factual, pouca variação |
| Sugestão de passeio / resposta aberta | 0.7 – 0.9 | mais natural, variado, "humano" |

```yaml
concierge:
  temperature-profiles:
    booking: 0.15
    faq: 0.35
    recommendation: 0.8
```

## 7. Multi-modelo com API keys

```java
public interface ChatModelPort {
    ChatModelResponse complete(ChatModelRequest request);
}
```

Cada adapter (`OpenAiChatModelAdapter`, `AnthropicChatModelAdapter`, `OllamaChatModelAdapter`) implementa a mesma porta. Um `ModelRouter` decide qual adapter usar:

- Por configuração explícita (`concierge.llm.primary-provider=anthropic`)
- Fallback automático se o provider primário falhar (timeout, erro 5xx, rate limit)
- Ollama local como opção sem custo para desenvolvimento/testes

```yaml
concierge:
  llm:
    primary-provider: anthropic
    fallback-provider: openai
    providers:
      openai:
        api-key: ${OPENAI_API_KEY}
        model: gpt-4o-mini
      anthropic:
        api-key: ${ANTHROPIC_API_KEY}
        model: claude-sonnet-4-6
      ollama:
        base-url: http://localhost:11434
        model: llama3.1
```

## 8. SDD — organização do repositório

```
docs/
├── specs/
│   ├── 01-rag-faq.md
│   ├── 02-tool-calling-booking.md
│   ├── 03-multi-model-router.md
│   └── 04-temperature-profiles.md
├── adr/
│   ├── ADR-001-hexagonal-para-ia.md
│   ├── ADR-002-redis-vs-pgvector.md
│   ├── ADR-003-estrategia-de-fallback-entre-provedores.md
│   └── ADR-004-quando-nao-usar-tool-calling.md
└── CLAUDE.md          # contexto para AI CLI (Claude Code) sobre o repo
```

Cada feature nasce como um `spec` antes do código: motivação, alternativas consideradas, critérios de aceite. Decisões de arquitetura relevantes viram ADR.

## 9. Roadmap sugerido

1. Domínio + porta LLM com um único adapter (Anthropic) — sem RAG, sem tools.
2. Adicionar RAG (Redis) para FAQ.
3. Adicionar tool calling para disponibilidade e preço (sem criar reserva ainda).
4. Adicionar `create_booking` com fluxo de confirmação.
5. Adicionar segundo e terceiro provider (OpenAI, Ollama) + `ModelRouter` com fallback.
6. Perfis de temperatura por caso de uso.
7. Documentar tudo em `docs/specs` e `docs/adr` retroativamente onde faltar.

## 10. Stack sugerida

- Java 21, Spring Boot 3.x, Spring AI (ChatClient, VectorStore, `@Tool`)
- Redis (vector search) ou pgvector
- OpenFeign para serviços internos (disponibilidade, preço, reserva)
- Testcontainers para testes de integração com Redis
