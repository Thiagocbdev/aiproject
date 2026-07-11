# Hotel Concierge AI

Concierge de hotel com IA multi-provider (Anthropic, OpenAI, Ollama) — Spring Boot microservices com RAG, tool calling e streaming SSE em tempo real.

## Quick start (Docker — sobe tudo)

```bash
# 1. Configure as API keys
cp .env.example .env      # e preencha ANTHROPIC_API_KEY e OPENAI_API_KEY

# 2. Suba infra + 3 microservices
docker-compose up -d --build

# 3. Baixe o modelo do Ollama (necessário na primeira vez)
docker exec hotel-ollama ollama pull llama3.2

# 4. Abra o front
#    front/hotel-concierge-dashboard.html (abrir direto no navegador)
```

Para derrubar tudo: `docker-compose down` (adicione `-v` para apagar os volumes/dados).

## Dev mode (infra no Docker, MSes na IDE)

Sobe apenas PostgreSQL, Redis Stack e Ollama — rode os microservices pelo IntelliJ/Maven:

```bash
docker-compose -f docker-compose.dev.yml up -d
docker exec hotel-ollama-dev ollama pull llama3.2

# depois, em cada MS:
./mvnw spring-boot:run
```

## Ollama — modelo

O container do Ollama sobe vazio; o modelo `llama3.2` precisa ser baixado uma vez
(fica persistido no volume `ollama_data`):

```bash
docker exec hotel-ollama ollama pull llama3.2        # compose completo
docker exec hotel-ollama-dev ollama pull llama3.2    # compose dev
```

## Portas e serviços

| Serviço | Container | Porta | Descrição |
|---|---|---|---|
| ms-hotel-concierge-ai | `hotel-ms-concierge` | 8080 | Orquestrador + AI (SSE, 3 LLMs em paralelo) |
| ms-hotel-info | `hotel-ms-info` | 8081 | Dados hoteleiros (PostgreSQL, Flyway) |
| ms-ai-data | `hotel-ms-ai-data` | 8082 | RAG / vector store / cache (Redis Stack) |
| PostgreSQL 16 | `hotel-postgres` | 5432 | Bancos `hotel_info` e `ai_data` (user/pass: `hotel`/`hotel`) |
| Redis Stack | `hotel-redis` | 6379 | Vector store + cache |
| RedisInsight | `hotel-redis` | 8001 | UI web do Redis (http://localhost:8001) |
| Ollama | `hotel-ollama` | 11434 | LLM local (llama3.2) |
| Front | — | — | `front/hotel-concierge-dashboard.html` (estático, sem build) |

## Variáveis de ambiente

Copie `.env.example` para `.env` na raiz (o docker-compose lê automaticamente):

| Variável | Obrigatória | Usada por | Descrição |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | Sim | ms-hotel-concierge-ai | API key da Anthropic (Claude) |
| `OPENAI_API_KEY` | Sim | ms-hotel-concierge-ai, ms-ai-data | API key da OpenAI (GPT-4o-mini + embeddings) |

O arquivo `.env` está no `.gitignore` — **nunca commite API keys**.

## Docs

- `docs/architecture/overview.md` — visão geral, fluxo, eventos SSE
- `docs/specs/` — specs de cada serviço
- `docs/swagger/` — contratos OpenAPI
