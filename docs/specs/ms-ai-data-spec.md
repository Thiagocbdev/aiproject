# ms-ai-data — Spec (AI Data: RAG + Cache + Training)

> Versão: 1.0 · Owner: Thiago · Atualizado: 2026-07-11
> Projeto: `ms-ai-data/` (a criar) · Swagger: `docs/swagger/ms-ai-data-openapi.yaml`

## 1. Responsabilidade

Serviço desacoplado para toda a camada de dados de IA. Consumido pelo `ms-hotel-concierge-ai` via Feign para:
1. **RAG** — busca semântica nos documentos do hotel (FAQ, políticas, cardápio, spa)
2. **Cache** — evita chamar o LLM para perguntas repetidas (TTL configurável)
3. **Training data** — registra cada conversa real para dataset futuro de avaliação/fine-tuning

## 2. Stack

| Item | Valor |
|---|---|
| Spring Boot | 3.5 · Java 21 · Porta **8082** |
| Vector Store | Spring AI 1.1.8 Redis Vector Store |
| Cache | Redis (mesma instância Redis Stack, namespace separado) |
| Training data | PostgreSQL 16 (tabela `training_example`) |
| Embedding | OpenAI text-embedding-3-small (via Spring AI OpenAI starter) |
| Dependências | `spring-boot-starter-web`, `spring-ai-starter-vector-store-redis`, `spring-ai-starter-model-openai` (só embedding), `spring-boot-starter-data-jpa`, `postgresql`, `spring-boot-starter-actuator`, `lombok` |

## 3. Endpoints REST (conforme swagger `ms-ai-data-openapi.yaml`)

### Vectors (RAG)

```
POST /api/v1/vectors/index
body: { id, content, metadata: { source, category } }
→ 201 Indexed

POST /api/v1/vectors/search
body: { query, topK: 3 }
→ 200 [ { id, content, score, metadata } ]

DELETE /api/v1/vectors/{id}
→ 204
```

### Cache

```
GET    /api/v1/cache/{key}    → 200 CacheEntry | 404
PUT    /api/v1/cache/{key}    body: { response, provider, ttlSeconds }  → 204
DELETE /api/v1/cache/{key}    → 204
```

> Chave de cache = `SHA256(provider + "|" + normalizedMessage)`. Calculada no orchestrator, passada aqui como string.

### Training examples

```
GET  /api/v1/training/examples  ?limit=50&provider=anthropic
POST /api/v1/training/examples
body: { provider, message, response, toolsUsed[], rating? }
→ 201
```

## 4. Documentos indexados para RAG

Populados na startup via `RagInitializer` (lê de `src/main/resources/rag/`):

| Arquivo | Conteúdo | Categoria |
|---|---|---|
| `hotel-manual.md` | Políticas gerais, horários, contatos, regras | `politicas` |
| `spa-faq.md` | Serviços de spa, preços, como reservar, cancelamento | `spa` |
| `restaurant-menu.md` | Cardápio, horários, opções veganas, reserva de mesa | `restaurante` |
| `local-attractions.md` | Pontos turísticos próximos, distâncias, dicas | `atrações` |

Chunking: 512 tokens por chunk, 50 tokens de overlap. Embedding: `text-embedding-3-small` (1536 dims). Similaridade: coseno, threshold 0.70.

## 5. Estrutura de pacotes

```
com.thiago.aidata
├── controller
│   ├── VectorController.java          # /vectors/index, /search, /{id}
│   ├── CacheController.java           # /cache/{key}
│   └── TrainingController.java        # /training/examples
├── service
│   ├── VectorService.java             # Spring AI VectorStore wrapper
│   ├── CacheService.java              # Redis ops (GET/SET/DEL com TTL)
│   └── TrainingService.java           # JPA crud de TrainingExample
├── repository
│   └── TrainingExampleRepository.java
├── model
│   └── TrainingExample.java           # @Entity
├── dto
│   ├── VectorIndexRequest.java
│   ├── VectorSearchRequest.java
│   ├── RetrievedChunk.java
│   ├── CacheEntry.java
│   ├── CacheEntryInput.java
│   ├── TrainingExample.java (DTO)
│   └── TrainingExampleInput.java
└── config
    ├── VectorStoreConfig.java         # Redis Vector Store bean
    ├── EmbeddingConfig.java           # OpenAI embedding bean
    ├── RedisConfig.java               # cache namespace
    └── RagInitializer.java            # indexa docs na startup
```

## 6. application.yml

```yaml
server:
  port: 8082

spring:
  application:
    name: ms-ai-data
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding.options:
        model: text-embedding-3-small
    vectorstore:
      redis:
        uri: redis://localhost:6379
        index: hotel-knowledge
        prefix: hotel:doc:
  datasource:
    url: jdbc:postgresql://localhost:5432/ai_data
    username: hotel
    password: hotel
  jpa:
    hibernate.ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration

rag:
  chunk-size: 512
  chunk-overlap: 50
  similarity-threshold: 0.70
  re-index-on-startup: false     # true só para reset do índice
```

## 7. Docker Compose (dev — Redis Stack)

```yaml
redis:
  image: redis/redis-stack:latest
  ports:
    - "6379:6379"
    - "8001:8001"    # RedisInsight UI
  volumes:
    - redis_data:/data
```

> Redis Stack é necessário para o módulo RediSearch que habilita o vector search. Redis simples não funciona para este caso.
