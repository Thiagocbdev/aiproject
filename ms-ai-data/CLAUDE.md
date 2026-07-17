# ms-ai-data — AI Data Service (RAG + Cache + Training)

**Porta:** 8082 | **Stack:** Spring Boot 3.5 · Spring AI 1.1.8 · Redis Stack · PostgreSQL 16 | **Java:** 21

## Responsabilidade

Camada desacoplada para todos os dados de IA. Consumido pelo `ms-hotel-concierge-ai` via Feign:

1. **RAG** — busca semântica em documentos do hotel (Redis Vector Store + embeddings)
2. **Cache** — respostas cacheadas por hash de pergunta + provider (Redis TTL)
3. **Training** — registra conversas reais para dataset futuro (PostgreSQL)
4. **Analytics** — histórico de tokens, custo e performance por sessão

## Documentação completa

- `../docs/specs/ms-ai-data-spec.md`
- `../docs/swagger/ms-ai-data-openapi.yaml`

## Endpoints

```
POST   /api/v1/vectors/index          { id, content, metadata }
POST   /api/v1/vectors/search         { query, topK }
DELETE /api/v1/vectors/{id}

GET    /api/v1/cache/{key}
PUT    /api/v1/cache/{key}            { response, provider, ttlSeconds }
DELETE /api/v1/cache/{key}
POST   /api/v1/cache/semantic/lookup
POST   /api/v1/cache/semantic

GET    /api/v1/training/examples      ?limit=&provider=
POST   /api/v1/training/examples
GET    /api/v1/training/examples/top

POST   /api/v1/sessions/{sessionId}
POST   /api/v1/sessions/{sessionId}/turns
GET    /api/v1/sessions/{sessionId}/turns
GET    /actuator/health
```

## Pacotes

```
com.thiago.aidata
├── controller/   VectorController, CacheController, TrainingController, SessionController, AnalyticsController
├── service/      VectorService, CacheService, TrainingService, SessionService
├── repository/   TrainingExampleRepository (JPA)
├── model/        TrainingExample, Session, Turn, TurnResponse
├── dto/          VectorIndexRequest, VectorSearchRequest, RetrievedChunk, CacheEntry, TrainingExampleInput
└── config/
    ├── VectorStoreConfig.java     — RedisVectorStore bean
    ├── EmbeddingConfig.java       — modelo de embedding (Ollama nomic-embed-text)
    ├── RedisConfig.java           — StringRedisTemplate para cache
    └── RagInitializer.java        — indexa documentos RAG na startup
```

## Documentos RAG

Em `src/main/resources/rag/` — indexados automaticamente na startup:
- `hotel-manual.md` — políticas, horários, contatos
- `spa-faq.md` — serviços de spa, preços, cancelamento
- `restaurant-menu.md` — cardápio, horários, reservas
- `local-attractions.md` — pontos turísticos, distâncias

Chunking: 512 tokens, overlap 50. Indexação idempotente — verifica se índice já existe antes de reindexar.

## Configuração

```yaml
server.port: 8082
spring.ai.ollama.embedding.options.model: nomic-embed-text
spring.ai.vectorstore.redis.uri: ${REDIS_URI:redis://localhost:6379}
spring.ai.vectorstore.redis.index: hotel-knowledge
spring.datasource.url: ${DB_URL:jdbc:postgresql://localhost:5432/ai_data}
rag.similarity-threshold: 0.70
rag.re-index-on-startup: false
```

## Infraestrutura

Requer **Redis Stack** (não Redis puro) — o módulo RediSearch é necessário para vector search.
Usar imagem `redis/redis-stack:latest`. RedisInsight disponível em `http://localhost:8001`.

## Dependências principais

`spring-boot-starter-web` · `spring-boot-starter-data-jpa` · `spring-ai-starter-vector-store-redis`
`spring-ai-bom (1.1.8)` · `postgresql` · `flyway-core` · `lombok`
