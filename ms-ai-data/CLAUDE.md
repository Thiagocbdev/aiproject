# ms-ai-data — AI Data MS (RAG + Cache + Training)

**Porta:** 8082 | **Stack:** Spring Boot 3.5 + Spring AI 1.1.8 (Redis Vector Store + OpenAI Embedding) + Redis Stack + PostgreSQL 16 | **Java:** 21

## Spec completa
`../docs/specs/ms-ai-data-spec.md`
`../docs/swagger/ms-ai-data-openapi.yaml`

## O que este serviço faz

Serviço desacoplado para toda camada de dados de IA. Consumido pelo `ms-hotel-concierge-ai` via Feign para:
1. **RAG** — busca semântica em documentos do hotel (redis vector search, embedding OpenAI)
2. **Cache** — respostas em cache por hash de pergunta+provider (Redis TTL)
3. **Training** — registra conversas reais para dataset futuro (PostgreSQL)

## Endpoints REST (alinhados ao swagger)
```
POST   /api/v1/vectors/index          { id, content, metadata }  → 201
POST   /api/v1/vectors/search         { query, topK }            → [ { id, content, score, metadata } ]
DELETE /api/v1/vectors/{id}                                       → 204

GET    /api/v1/cache/{key}                                        → 200 CacheEntry | 404
PUT    /api/v1/cache/{key}            { response, provider, ttlSeconds }  → 204
DELETE /api/v1/cache/{key}                                        → 204

GET    /api/v1/training/examples      ?limit=50&provider=        → []
POST   /api/v1/training/examples      { provider, message, response, toolsUsed[], rating? }  → 201
GET    /actuator/health
```

## Estrutura de pacotes a criar (projeto do zero)
```
com.thiago.aidata
├── controller/   VectorController, CacheController, TrainingController
├── service/      VectorService, CacheService, TrainingService
├── repository/   TrainingExampleRepository (JPA)
├── model/        TrainingExample (@Entity)
├── dto/          VectorIndexRequest, VectorSearchRequest, RetrievedChunk, CacheEntry, CacheEntryInput, TrainingExampleInput, TrainingExampleResponse
└── config/
    ├── VectorStoreConfig.java     # RedisVectorStore bean
    ├── EmbeddingConfig.java       # OpenAI text-embedding-3-small
    ├── RedisConfig.java           # StringRedisTemplate para cache
    └── RagInitializer.java        # popula vector store na startup
```

## Documentos RAG (em src/main/resources/rag/)
Criar estes 4 arquivos com conteúdo realista:
- `hotel-manual.md` — políticas, horários, contatos, regras do hotel
- `spa-faq.md` — serviços de spa, preços, como reservar, cancelamento
- `restaurant-menu.md` — cardápio, horários, reserva de mesa, opções vegetarianas
- `local-attractions.md` — pontos turísticos, distâncias, dicas

Chunking: 512 tokens, 50 overlap. `RagInitializer` lê esses arquivos, quebra em chunks e indexa via `VectorStore.add()` na startup (idempotente — verificar se índice já existe).

## pom.xml — dependências principais
```xml
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-validation
spring-boot-starter-actuator
spring-ai-starter-vector-store-redis
spring-ai-starter-model-openai  <!-- só embedding, não chat -->
spring-ai-bom (1.1.8)
postgresql (driver)
flyway-core
lombok
spring-boot-testcontainers (test)
testcontainers:postgresql (test)
```

## application.yml
```yaml
server.port: 8082
spring.ai.openai.api-key: ${OPENAI_API_KEY}
spring.ai.openai.embedding.options.model: text-embedding-3-small
spring.ai.vectorstore.redis.uri: redis://localhost:6379
spring.ai.vectorstore.redis.index: hotel-knowledge
spring.ai.vectorstore.redis.prefix: hotel:doc:
spring.datasource.url: jdbc:postgresql://localhost:5432/ai_data
spring.datasource.username: hotel
spring.datasource.password: hotel
rag.similarity-threshold: 0.70
rag.re-index-on-startup: false
```

## IMPORTANTE — Redis Stack
Usar `redis/redis-stack:latest` (não redis simples). O módulo RediSearch é necessário para vector search. Verificar que o índice existe antes de tentar buscar.
