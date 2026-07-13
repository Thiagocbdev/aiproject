# Hotel Concierge AI

Concierge de hotel com IA multi-provider em tempo real — Spring Boot 3.5 microservices com RAG, tool calling e SSE fan-out para 3 LLMs em paralelo.

## Stack

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 21 + Spring Boot 3.5 |
| IA | Spring AI 1.1.8 |
| Providers LLM | OpenRouter · Nemotron 120B · Gemini 3.1 Flash Lite · Ollama llama3.2 |
| Vector Store | Redis Stack + nomic-embed-text (embeddings) |
| Cache | Redis (TTL 48h) |
| Base de dados | PostgreSQL 16 + Flyway |
| Frontend | HTML5 puro, sem build, EventSource SSE |

## Microserviços

| MS | Porta | Responsabilidade |
|----|-------|-----------------|
| `ms-hotel-concierge-ai` | 8080 | Orquestrador: fan-out SSE para 3 LLMs, RAG, tool calling |
| `ms-hotel-info` | 8081 | Dados do hotel: hóspedes, quartos, reservas, preços (PostgreSQL) |
| `ms-ai-data` | 8082 | Camada IA: RAG vector store, cache, dados de treino (Redis + PostgreSQL) |

---

## Quick start — tudo em Docker

> **Pré-requisito:** Ollama a correr nativamente com `llama3.2` e `nomic-embed-text` instalados.

```bash
# 1. Configurar API keys
cp .env.example .env
# preencher OPENROUTER_API_KEY e GEMINI_API_KEY no .env

# 2. Subir infra + 3 MSes
docker compose up -d

# 3. Abrir o front (ficheiro estático, sem servidor)
#    front/hotel-concierge-dashboard.html
```

Verificar que tudo está a funcionar:
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

---

## Dev mode — infra Docker, MSes locais (IntelliJ / terminal)

```bash
# 1. Só a infra em Docker
docker compose up -d postgres redis

# 2. Ollama já corre nativamente (http://localhost:11434)

# 3. Iniciar cada MS (PowerShell — carrega .env automaticamente)
.\run-local.ps1 hotel-info    # porta 8081
.\run-local.ps1 ai-data       # porta 8082
.\run-local.ps1 concierge     # porta 8080

# Ou no IntelliJ: usar as run configs em .run/ (Spring Boot)
# Para o concierge: Modify options → Load variables from .env file
```

Parar MSes Docker para correr localmente:
```bash
docker compose stop ms-hotel-info ms-ai-data ms-hotel-concierge-ai
# voltar para Docker:
docker compose up -d ms-hotel-info ms-ai-data ms-hotel-concierge-ai
```

---

## Variáveis de ambiente

Copie `.env.example` para `.env` na raiz do projecto:

```env
OPENROUTER_API_KEY=sk-or-v1-...   # https://openrouter.ai/keys
GEMINI_API_KEY=AIza...            # https://aistudio.google.com/app/apikey
```

> `.env` está no `.gitignore` — **nunca commite API keys**.
>
> Em modo local, o `ms-hotel-concierge-ai` carrega o `.env` automaticamente na startup.

---

## Providers LLM

| Slot interno | Modelo | Temperatura | Papel |
|-------------|--------|-------------|-------|
| `anthropic` | OpenRouter · Nemotron 120B | 0.15 | Reservas e precisão (com tools + RAG) |
| `openai` | Gemini 3.1 Flash Lite | 0.35 | FAQ e documentos (RAG only, sem tools) |
| `ollama` | llama3.2 (local) | 0.80 | Recomendações criativas (cache only) |

---

## Infraestrutura Docker

| Serviço | Container | Porta | Descrição |
|---------|-----------|-------|-----------|
| PostgreSQL 16 | `hotel-postgres` | 5432 | DBs `hotel_info` e `ai_data` (user/pass: `hotel`/`hotel`) |
| Redis Stack | `hotel-redis` | 6379 + 8001 | Vector store + cache + RedisInsight UI |
| Ollama | nativo Windows | 11434 | llama3.2 (chat) + nomic-embed-text (embeddings) |

### Instalar modelos Ollama (primeira vez)
```bash
ollama pull llama3.2
ollama pull nomic-embed-text
```

---

## Ollama — porquê nativo?

O Ollama requer acesso directo à GPU/CPU do host para inferência eficiente. Correr em Docker num ambiente Windows sem passthrough de GPU resulta em tempos de resposta de 60–150s por pedido. Correndo nativamente os tempos são mais baixos e o setup é mais simples.

---

## Docs

| Ficheiro | Conteúdo |
|----------|----------|
| `docs/architecture/overview.md` | Arquitectura, fluxo SSE, eventos |
| `docs/postman/` | Collections Postman para os 3 MSes |
| `docs/swagger/` | Contratos OpenAPI YAML |
| `docs/specs/` | Specs originais de cada serviço |
| `ms-hotel-info/README.md` | Endpoints e entidades do serviço de dados |
| `ms-ai-data/README.md` | Endpoints RAG, cache e sessões |
| `ms-hotel-concierge-ai/README.md` | Endpoints SSE, providers e tools |

---

## Estrutura do projecto

```
aiproject/
├── front/
│   └── hotel-concierge-dashboard.html   ← dashboard SSE (abrir directamente)
├── ms-hotel-concierge-ai/               ← orquestrador + IA (porta 8080)
├── ms-hotel-info/                       ← dados do hotel (porta 8081)
├── ms-ai-data/                          ← RAG + cache (porta 8082)
├── docs/
│   ├── architecture/overview.md
│   ├── postman/                         ← collections Postman
│   ├── swagger/                         ← contratos OpenAPI
│   └── specs/                           ← specs de cada MS
├── .run/                                ← run configs IntelliJ
├── run-local.ps1                        ← script PowerShell modo local
├── docker-compose.yml
└── .env.example
```
