# Hotel Concierge AI — Raiz do Projeto

## Arquitetura (4 peças)

```
front/hotel-concierge-dashboard.html   (HTML puro, aprovado — não alterar)
ms-hotel-concierge-ai/   porta 8080   (orchestrator + AI: Spring AI, 3 LLMs em paralelo)
ms-hotel-info/           porta 8081   (hotel data: Spring Boot + PostgreSQL)
ms-ai-data/              porta 8082   (AI data: Spring AI Redis Vector Store + cache + training)
```

## Docs de referência
- `docs/architecture/overview.md` — visão geral, fluxo, eventos SSE
- `docs/specs/ms-hotel-concierge-ai-spec.md` — orchestrator
- `docs/specs/ms-hotel-info-spec.md` — hotel data
- `docs/specs/ms-ai-data-spec.md` — AI data
- `docs/swagger/` — contratos OpenAPI de cada serviço

## Docker
- `docker-compose.yml` na raiz sobe **todos os serviços** (PostgreSQL, Redis Stack, Ollama + 3 MSes)
- Cada MS tem seu próprio `Dockerfile` na raiz do projeto
- Porta padrão por serviço: 8080, 8081, 8082
- Infra: PostgreSQL 16 (5432), Redis Stack (6379 + 8001 RedisInsight), Ollama (11434)

## Variáveis de ambiente necessárias
```
OPENROUTER_API_KEY=sk-or-...   # substitui ANTHROPIC_API_KEY + OPENAI_API_KEY
```

## Para rodar tudo
```bash
docker-compose up -d                          # sobe infra + 3 MSes
# ou em dev (infra só):
docker-compose up -d postgres redis ollama
# e cada MS no IntelliJ/Maven
```
