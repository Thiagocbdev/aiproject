# Hotel Concierge AI — Tasks & Planejamento

> Atualizado: 2026-07-11 | Owner: Thiago

## Status dos Agentes

| Serviço | Status | Observações |
|---|---|---|
| `ms-hotel-info` | ✅ CONCLUÍDO | 38 arquivos criados, bug V2 migration corrigido |
| `ms-ai-data` | ✅ CONCLUÍDO | 30 arquivos criados, @ElementCollection para tools_used |
| `ms-hotel-concierge-ai` | ✅ CONCLUÍDO | todos os arquivos criados, correções manuais aplicadas |

---

## Checklist Geral

### Infraestrutura
- [x] `docker-compose.yml` já existia (bem configurado com healthchecks, env vars)
- [x] `docker-compose.dev.yml` já existia (só infra: postgres, redis, ollama)
- [x] `docker/postgres/init-multiple-dbs.sh` já existia (cria hotel_info + ai_data)
- [x] `.env.example` já existia (ANTHROPIC_API_KEY, OPENAI_API_KEY)
- [ ] Testar `docker-compose -f docker-compose.dev.yml up -d`

### ms-hotel-info (porta 8081) ✅
- [x] pom.xml criado (Spring Boot 3.5.0, JPA, PostgreSQL, Flyway, Lombok)
- [x] application.yml criado + alinhado com env vars docker-compose (DB_URL, DB_USER, DB_PASS)
- [x] V1__create_tables.sql (guests, rooms, service_types, service_bookings, room_bookings)
- [x] V2__seed_data.sql corrigido (bug DATE+TIME fixado: `CURRENT_DATE + 1 + slot_time`)
- [x] Entidades JPA (Guest, Room, ServiceType, ServiceBooking, RoomBooking)
- [x] Repositories (5 repos com queries customizadas)
- [x] Services (GuestService, RoomService, AvailabilityService, BookingService)
- [x] Controllers (GuestController, RoomController, PricingController, BookingController)
- [x] DTOs + GlobalExceptionHandler (404/409)
- [x] DataInitializer (loga contagem na startup)
- [x] CorsConfig (allow *)
- [x] Dockerfile (eclipse-temurin:21-jre-alpine)
- [x] Teste @Disabled

### ms-ai-data (porta 8082) ✅
- [x] pom.xml criado (Spring Boot 3.5.0, Spring AI 1.1.8, Redis, PostgreSQL, Flyway)
- [x] application.yml criado + alinhado com env vars docker-compose (DB_URL, DB_USER, DB_PASS, REDIS_URI, REDIS_HOST, REDIS_PORT)
- [x] V1__create_training_examples.sql (training_examples + training_example_tools)
- [x] TrainingExampleEntity (@ElementCollection para tools_used — sem dep extra)
- [x] VectorService (Spring AI VectorStore auto-configurado)
- [x] CacheService (RedisTemplate<String,String> com JSON + TTL)
- [x] TrainingService (JPA + paginação)
- [x] Controllers (VectorController, CacheController, TrainingController)
- [x] VectorStoreConfig (vazio — auto-config)
- [x] RedisConfig (StringRedisSerializer)
- [x] RagInitializer (indexa docs .md se rag.re-index-on-startup=true)
- [x] RAG docs (hotel-manual.md, spa-faq.md, restaurant-menu.md, local-attractions.md)
- [x] CorsConfig
- [x] Dockerfile
- [x] Teste @Disabled

### ms-hotel-concierge-ai (porta 8080) ✅
- [x] pom.xml corrigido (3.5.16.RELEASE → 3.5.0, Spring Cloud 2025.0.0)
- [x] pom.xml: Testcontainers deps adicionadas (para TestcontainersConfiguration.java do scaffold)
- [x] application.yml criado (substituiu application.properties)
- [x] AiConfig (3 ChatClient beans: anthropic/openai/ollama com system prompts)
- [x] CorsConfig (allow *)
- [x] AsyncConfig (@EnableAsync)
- [x] HotelInfoClient (Feign → ${concierge.hotel-info-url})
- [x] AiDataClient (Feign → ${concierge.ai-data-url})
- [x] HotelTools (@Tool: 5 ferramentas, ThreadLocal para contexto requestId+provider)
- [x] SseSessionStore (ConcurrentHashMap emitters + pendingActions)
- [x] ProviderOrchestrator (RAG → LLM → tokens SSE → metrics → done/error)
- [x] ConciergeService (fan-out via VirtualThreadExecutor + CountDownLatch)
- [x] ConciergeController (/ask, /stream/{requestId}, /confirm-booking)
- [x] ProviderController (/providers, /temperature-profiles)
- [x] Model classes (AskRequest, AskAccepted, ConfirmBookingRequest/Response, etc.)
- [x] MsHotelConciergeAiApplication (@EnableFeignClients adicionado)
- [x] lombok.config (copyableAnnotations Qualifier para @RequiredArgsConstructor)
- [x] Dockerfile (multi-stage: Maven build + JRE runtime com curl + non-root user)
- [x] Teste @Disabled

---

## Revisão pós-agentes ✅

- [x] pom.xml do ms-hotel-concierge-ai corrigido (3.5.0)
- [x] application.properties substituído por application.yml
- [x] @EnableFeignClients na main class
- [x] OllamaOptions usa `org.springframework.ai.ollama.api.OllamaOptions` (correto)
- [x] tools_used usa @ElementCollection (tabela training_example_tools)
- [x] lombok.config criado para @Qualifier + @RequiredArgsConstructor
- [x] docker-compose.yml: OLLAMA_URL → OLLAMA_BASE_URL (alinhado com application.yml)
- [x] application.yml do ms-hotel-info: env vars ${DB_URL}, ${DB_USER}, ${DB_PASS}
- [x] application.yml do ms-ai-data: env vars ${REDIS_URI}, ${REDIS_HOST}, ${DB_URL}...
- [ ] **Próximo passo**: Compilar os 3 MSes: `mvn package -DskipTests`

---

## Problemas Conhecidos / Riscos

| Risco | Mitigação |
|---|---|
| Spring Boot `3.5.16.RELEASE` inválido no pom.xml existente | Agente instrudo a corrigir para `3.5.0` |
| Spring AI OllamaOptions import pode variar | Agente instrudo a tentar `org.springframework.ai.ollama.api.OllamaOptions` |
| Redis Vector Store auto-config vs bean manual | Agente instrudo a NÃO declarar bean manual |
| tools_used como array PostgreSQL | Agente instrudo a usar @ElementCollection |
| Feign falha se ms-hotel-info/ms-ai-data offline | RAG e tools têm try-catch com fallback |

---

## Como rodar (após agentes terminarem)

```bash
# 1. Subir infraestrutura
cd aiproject
docker-compose up -d postgres redis ollama

# 2. Pull do modelo Ollama
docker exec ollama ollama pull llama3.2

# 3. Compilar e subir cada MS (ou usar docker-compose up -d para tudo)
cd ms-hotel-info && mvn package -DskipTests && java -jar target/ms-hotel-info-0.0.1-SNAPSHOT.jar
cd ms-ai-data && mvn package -DskipTests && OPENAI_API_KEY=sk-... java -jar target/ms-ai-data-0.0.1-SNAPSHOT.jar
cd ms-hotel-concierge-ai && mvn package -DskipTests && ANTHROPIC_API_KEY=sk-ant-... OPENAI_API_KEY=sk-... java -jar target/hotel-concierge-ai-0.0.1-SNAPSHOT.jar

# 4. Indexar RAG (após ms-ai-data subir)
# Mude rag.re-index-on-startup=true no application.yml uma vez, depois volte para false

# 5. Abrir o front
# Abra aiproject/front/hotel-concierge-dashboard.html no browser
```
