# Hotel Concierge AI — Guia de Apresentação

> Projeto portfólio · Stack: Spring Boot 3.5 + Spring AI 1.1.8 + 3 LLMs em paralelo

---

## O que é este projeto?

Um **concierge de hotel com IA** que demonstra, em um único fluxo visual, as principais técnicas de engenharia de sistemas com LLMs:

- Múltiplos modelos de linguagem respondendo **em paralelo**, cada um com um papel diferente
- **RAG** (busca em base de conhecimento do hotel antes de responder)
- **Cache inteligente** (mesma pergunta → sem chamar a API de novo)
- **Tool Calling** (a IA aciona funções reais: consulta disponibilidade, cria reserva)
- **Histórico de conversa** (contexto acumulado entre perguntas)
- **Dashboard de analytics** (evolução de tokens e economia de cache ao longo do tempo)

---

## Os três modelos — papéis intencionalmente diferentes

| Provider | Modelo | Temperatura | Papel |
|---|---|---|---|
| **Anthropic** | claude-sonnet-4-6 | 0.15 (conservador) | Reservas e operações críticas — respostas precisas e seguras |
| **OpenAI** | gpt-4o-mini | 0.35 (balanceado) | FAQ e informações — RAG + fatos do hotel |
| **Ollama** | llama3.2 (local) | 0.80 (criativo) | Sugestões e recomendações — roda sem API key, na máquina local |

**Por que temperaturas diferentes?**
Temperatura controla o quanto o modelo "inventa" na resposta. Uma reserva errada de horário é um problema; uma sugestão de passeio criativa é um benefício. O mesmo sistema de IA, calibrado de forma diferente para cada contexto.

---

## Arquitetura (resumida)

```
[Front HTML] ──POST /ask──▶ [ms-hotel-concierge-ai :8080]
                                    │
                         ┌──────────┼──────────┐
                         ▼          ▼          ▼
                    Anthropic    OpenAI     Ollama
                    (cloud)     (cloud)    (local)
                         │          │          │
                    SSE stream em tempo real por coluna
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
   [ms-hotel-info :8081]   [ms-ai-data :8082]
   PostgreSQL — hóspedes,   Redis — embeddings,
   quartos, reservas        cache, training data
```

**Três microserviços:**
- `ms-hotel-concierge-ai` (8080) — orquestrador: recebe a pergunta, dispara os 3 LLMs em paralelo, transmite os eventos via SSE
- `ms-hotel-info` (8081) — dados do hotel: hóspedes, quartos, disponibilidade, reservas (PostgreSQL)
- `ms-ai-data` (8082) — dados de IA: base vetorial RAG, cache Redis, histórico de sessões

---

## O que acontece quando você envia uma pergunta?

```
1. Cache check     → "já respondi isso antes?" → se sim, retorna em milissegundos
2. RAG search      → busca no manual do hotel, cardápio, FAQ do spa (Redis Vector Store)
3. Monta o prompt  → pergunta + contexto RAG + histórico da conversa (se contexto ativo)
4. Chama o LLM     → tokens chegam progressivamente (streaming)
5. Tool calling    → se a IA decide agir (reservar, consultar preço), chama o ms-hotel-info
6. Persiste        → salva a sessão, tokens, duração no PostgreSQL (analytics)
```

Tudo isso é visível **em tempo real** no front, por coluna, por provider.

---

## Cinco pontos técnicos para a apresentação

### 1. SSE Fan-out — 3 LLMs em paralelo

O orquestrador dispara os 3 LLMs em **threads virtuais simultâneas** (Java 21 Virtual Threads) e transmite os eventos de cada um via uma única conexão SSE. O front diferencia o provider pelo campo `"provider"` em cada evento.

**O que mostrar:** envie uma pergunta e observe as três colunas preenchendo ao mesmo tempo, cada uma no seu ritmo.

**Ponto técnico:** `CountDownLatch` garante que o orquestrador aguarda todos os providers antes de fechar o stream, mas sem bloquear threads do servidor.

---

### 2. RAG — Retrieval-Augmented Generation

Antes de chamar qualquer LLM, o sistema faz uma busca semântica em documentos do hotel indexados no Redis Vector Store (embeddings gerados pelo modelo de embedding da OpenAI/Spring AI).

**O que mostrar:** badge `rag ×2` aparece na coluna antes da resposta chegar. O log mostra `rag_search → 2 chunks`. A resposta usa informações reais do hotel (horários do spa, menu do restaurante, taxas de quarto).

**Ponto técnico:** os documentos estão em `ms-ai-data/src/main/resources/rag/` — texto simples sobre o hotel. Qualquer documento novo indexado via `POST /api/v1/vectors/index` passa a ser usado imediatamente nas próximas perguntas.

---

### 3. Cache Redis — economia de tokens

Após a primeira resposta de um LLM, ela é salva no Redis com chave `{provider}:{hash_da_pergunta}`. Perguntas idênticas subsequentes retornam da cache sem chamar a API.

**O que mostrar:** envie a mesma pergunta duas vezes. Na segunda, o badge `cache hit ⚡` aparece e a resposta chega em milissegundos. Os tokens in/out ficam em zero — nenhum token foi gasto.

**Ponto técnico:** a chave é `Math.abs(message.hashCode())` — simples e sem dependência de biblioteca. TTL configurado em 1 hora. Em produção, o hash pode ser normalizado (lowercase, sem pontuação) para aumentar o hit rate.

**Para o dashboard de analytics:** a tabela `turn_responses` registra `cache_hit=true/false` e `tokens_in/out`. O analytics mostra quanto foi economizado em tokens no histórico.

---

### 4. Tool Calling — a IA age no sistema

Quando o usuário pede uma reserva, a IA não apenas responde com texto: ela chama funções reais que executam operações no `ms-hotel-info`.

**Ferramentas disponíveis:**

| Ferramenta | O que faz |
|---|---|
| `check_availability` | Consulta disponibilidade de quarto/serviço em data/hora |
| `get_price` | Retorna tarifa atual para tipo de quarto |
| `create_booking` | Cria reserva (aguarda confirmação humana antes de executar) |
| `get_guest_profile` | Busca dados do hóspede pelo ID |
| `search_local_attractions` | Lista atrações locais com distância e preço |

**Safety gate:** `create_booking` nunca executa automaticamente. O orchestrador emite `tool_call` com um `pendingActionId`, aguarda o hóspede confirmar via `POST /confirm-booking`, e só então executa.

**Limitação honesta (importante para apresentação):**

> **Ollama (llama3.2) não executa tool calling de forma confiável.**
>
> Modelos locais pequenos (3B parâmetros) geralmente não seguem o schema de function calling de forma consistente. O llama3.2 pode ignorar a chamada de ferramenta ou retorná-la em formato incorreto.
>
> **Na prática:** Anthropic e OpenAI executam tool calls com alta fidelidade. O Ollama é posicionado para recomendações e sugestões (temperatura alta, texto criativo), não para operações transacionais.
>
> **Isso é uma decisão de design realista:** em produção, você usa o modelo certo para o trabalho certo. Modelos locais têm custo zero e latência baixa para geração de texto; modelos cloud têm função calling confiável para operações críticas.

---

### 5. Threads e histórico de conversa

O front tem dois modos de conversa:

**One-shot (contexto desativado):** cada pergunta é independente. O LLM responde sem saber o que foi perguntado antes. Aparece como `T1`, `T2`, `T3`... (Threads independentes).

**Com contexto (botão ativado):** as perguntas ficam encadeadas. O orchestrador busca o histórico da sessão no PostgreSQL e monta um prompt com as perguntas e respostas anteriores. Aparece como `T1 → M1, M2, M3...` (uma Thread com múltiplas Mensagens).

Acima de cada `M` está visível:
- `↑{tokens_in} ↓{tokens_out}` — custo daquela mensagem específica
- `⏱{duração}` — tempo de resposta do LLM
- No header da Thread: total acumulado de tokens (M1 + M2 + ...)
- No topo da coluna: total da sessão inteira

---

## Roteiro de demo sugerido (10 minutos)

**1. Pergunta simples — RAG em ação (1 min)**
> "que horas abre o spa?"

Mostra os badges RAG nas colunas, resposta com informação real do hotel.

**2. Mesma pergunta — cache hit (30 seg)**
> "que horas abre o spa?" (novamente)

Badge `⚡ cache` aparece, resposta em milissegundos, tokens = 0.

**3. Pergunta de disponibilidade — tool calling (2 min)**
> "tem algum quarto disponível para amanhã?"

Mostra `tool_call: check_availability(...)` no log da coluna Anthropic/OpenAI. `tool_result` com a resposta do ms-hotel-info.

**4. Ativar contexto — conversa encadeada (3 min)**
Ativar botão "contexto ativado", enviar:
> "e o restaurante, serve jantar?"
> "qual o valor do menu do chef?"

Mostrar T1 → M1 → M2 → M3. Header da thread acumulando tokens. Provar que o LLM sabe o que foi dito antes.

**5. Dashboard de analytics (1 min)**
Abrir `analytics-dashboard.html`. Mostrar tokens acumulados, economia de cache, evolução por provider.

---

## Perguntas técnicas frequentes em apresentação

**"Por que três LLMs ao mesmo tempo?"**
Para demonstrar que modelos diferentes têm personalidades e velocidades diferentes para a mesma pergunta. Em produção, você escolheria um — aqui, a comparação simultânea é o ponto.

**"Por que não Next.js/React no front?"**
HTML puro foi uma decisão deliberada: o foco é o backend e a arquitetura de AI, não o frontend. Um único arquivo de 400 linhas demonstra o mesmo sem build step, deploy, ou dependências.

**"O Redis é necessário para tudo isso?"**
Redis faz dois trabalhos aqui: Vector Store (embeddings/RAG, requer o módulo RediSearch do Redis Stack) e Cache de respostas (Redis puro, TTL simples). Em produção, você poderia separar os dois.

**"Qual é o custo real de rodar isso?"**
Com Ollama local: zero de API cost. Com Anthropic + OpenAI: frações de centavo por pergunta (claude-sonnet-4-6 ≈ $0.003 por 1K tokens, gpt-4o-mini ≈ $0.00015). O cache reduz esse custo ao longo do tempo.

---

## Como rodar para a demo

```bash
# Pré-requisito: Docker Desktop rodando

# 1. Subir infraestrutura (PostgreSQL + Redis Stack + Ollama)
cd aiproject
docker-compose -f docker-compose.dev.yml up -d

# 2. Setar Java 21 (Windows PowerShell)
$env:PATH = "C:\Program Files\Java\jdk-21.0.8\bin;" + $env:PATH

# 3. Subir os 3 microserviços (cada um em terminal separado)
cd ms-hotel-info     && java -jar target/ms-hotel-info-0.0.1-SNAPSHOT.jar
cd ms-ai-data        && java -jar target/ms-ai-data-0.0.1-SNAPSHOT.jar
cd ms-hotel-concierge-ai && java -jar target/hotel-concierge-ai-0.0.1-SNAPSHOT.jar

# 4. Abrir o front
# Abrir aiproject/front/hotel-concierge-dashboard.html no browser
# Analytics: aiproject/front/analytics-dashboard.html
```

**Variáveis de ambiente opcionais** (para Anthropic e OpenAI):
```bash
$env:ANTHROPIC_API_KEY = "sk-ant-..."
$env:OPENAI_API_KEY    = "sk-..."
```
Sem as keys, apenas a coluna Ollama responde — o resto mostra `401 · sem API key`.

---

## Estrutura do repositório

```
aiproject/
├── front/
│   ├── hotel-concierge-dashboard.html   ← demo principal (abrir no browser)
│   └── analytics-dashboard.html         ← dashboard de tokens e cache
├── ms-hotel-concierge-ai/               ← porta 8080 · orquestrador + AI
├── ms-hotel-info/                       ← porta 8081 · dados do hotel
├── ms-ai-data/                          ← porta 8082 · RAG + cache + analytics
└── docs/
    ├── APRESENTACAO.md                  ← este arquivo
    ├── architecture/overview.md         ← diagrama técnico detalhado
    ├── postman/                         ← collections para testar as APIs
    └── swagger/                         ← contratos OpenAPI dos 3 serviços
```
