# Hotel Concierge AI — Visão Geral do Projeto

## Arquitetura

```
front/hotel-concierge-dashboard.html   — UI principal (HTML puro, sem build step)
ms-hotel-concierge-ai/   porta 8080   — orquestrador: Spring AI, 3 LLMs em paralelo via SSE
ms-hotel-info/           porta 8081   — dados do hotel: Spring Boot + PostgreSQL
ms-ai-data/              porta 8082   — camada de IA: Redis Vector Store, cache, training
```

## Documentação

- `docs/APRESENTACAO.md` — guia de demo e pontos técnicos para apresentação
- `docs/architecture/overview.md` — diagrama técnico detalhado e fluxo de eventos
- `docs/swagger/` — contratos OpenAPI dos 3 serviços
- `docs/postman/` — collections prontas para testar as APIs

## Docker

`docker-compose.yml` na raiz sobe todos os serviços:
- PostgreSQL 16 (porta 5432)
- Redis Stack com RediSearch (porta 6379 + RedisInsight em 8001)
- ms-hotel-info, ms-ai-data, ms-hotel-concierge-ai

```bash
docker compose up -d
```

## Variáveis de ambiente

Criar arquivo `.env` na raiz (ver `.env.example`):
```
OPENROUTER_API_KEY=sk-or-...
GEMINI_API_KEY=...
```

## Stack

Java 21 · Spring Boot 3.5 · Spring AI 1.1.8 · PostgreSQL 16 · Redis Stack · Docker
