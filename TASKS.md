# Hotel Concierge AI — Tasks & Planejamento

> Atualizado: 2026-07-12 | Owner: Thiago

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

## V1 — Gaps Identificados (pós-revisão de código)

> Itens declarados na spec/contrato mas não conectados na implementação atual.

### GAP-01 — Cache Redis não está sendo usado no fluxo de resposta ✅ IMPLEMENTADO
- **Arquivo:** `ms-hotel-concierge-ai/.../service/ProviderOrchestrator.java`
- **Situação:** `AiDataClient` declara `getCache(key)` e `putCache(key, body)`, mas `callProvider()` nunca os chama.
- **Impacto:** toda pergunta chama o LLM mesmo que já tenha resposta cacheada — custo desnecessário de API e latência extra.
- **O que implementar:**
  - [ ] Antes de chamar o LLM: `getCache(key)` → se hit, emitir evento `cache_hit` e retornar sem chamar o LLM
  - [ ] Após resposta do LLM: `putCache(key, { response, provider, ttlSeconds })` para cachear
  - [ ] Chave de cache: hash de `provider + message` (ex: `MD5` ou concatenação simples)

### GAP-03 — Front não persiste histórico e não suporta modos de conversa ✅ IMPLEMENTADO
- **Arquivo:** `front/hotel-concierge-dashboard.html`
- **Referência visual:** `aifront.drawio.png` — cada coluna acumula response 1 → response 2 → response x para baixo; live log e métricas no topo da coluna.
- **Situação atual:** cada envio chama `resetForSend()` que apaga as 3 colunas. `AskRequest` já tem `sessionId` mas não é usado.
- **Regra fundamental:** perguntas/respostas anteriores NUNCA saem da tela — em nenhum dos dois modos.

  **Modo 1 — One-shot (botão desativado)**
  - Cada pergunta é enviada isolada (sem contexto anterior no prompt do LLM)
  - Nova pergunta appenda um separador de turno em cada coluna; resposta cresce abaixo
  - `sessionId` enviado apenas para logging/analytics (o back não usa como contexto)

  **Modo 2 — Contexto ativado (botão toggle na topbar)**
  - As respostas anteriores são enviadas como histórico no prompt do LLM
  - Visualmente: mesmo comportamento de acúmulo — separador de turno + nova resposta abaixo
  - `sessionId` fixo por sessão de página enviado; back monta histórico

  **Front — o que implementar:**
  - [ ] Remover `resetForSend()` do fluxo; substituir por `appendTurnSeparator(question)` em cada coluna
  - [ ] Botão toggle "contexto" na topbar (estado visual: ativo/inativo)
  - [ ] `let sessionId = crypto.randomUUID()` gerado uma vez na carga da página
  - [ ] Enviar `{ message, providers, sessionId, useContext: bool }` no POST
  - [ ] Live log e badges acumulam por turno (prefixar com número do turno: `[T2] > rag_search...`)

  **Back — o que implementar (ms-hotel-concierge-ai):**
  - [ ] `AskRequest` adicionar campo `useContext: boolean`
  - [ ] `ConversationStore`: `Map<sessionId, List<{question, responses}>>` em memória
  - [ ] `ProviderOrchestrator`: quando `useContext=true`, montar histórico como prefixo do prompt
  - [ ] Após `done`: salvar resposta no `ConversationStore` + chamar `ms-ai-data` para persistir na sessão DB

### GAP-04 — Sessões e conversas não são persistidas no banco ✅ IMPLEMENTADO
- **Serviço:** `ms-ai-data` (tabelas novas) + `ms-hotel-concierge-ai` (chamadas ao salvar)
- **Motivação:** demonstrar evolução de tokens no tempo, efetividade do cache e economia de custo.
- **Tabelas a criar em `ai_data` (nova migration V3):**

  ```sql
  -- Uma sessão = uma abertura do front (sessionId do browser)
  CREATE TABLE sessions (
    id          VARCHAR(36) PRIMARY KEY,  -- sessionId do front
    created_at  TIMESTAMP DEFAULT NOW(),
    ended_at    TIMESTAMP
  );

  -- Um turno = uma pergunta e suas respostas dos 3 providers
  CREATE TABLE conversation_turns (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(36) REFERENCES sessions(id),
    question        TEXT NOT NULL,
    use_context     BOOLEAN DEFAULT FALSE,
    turn_number     INT NOT NULL,
    created_at      TIMESTAMP DEFAULT NOW()
  );

  -- Uma resposta por provider por turno
  CREATE TABLE turn_responses (
    id              BIGSERIAL PRIMARY KEY,
    turn_id         BIGINT REFERENCES conversation_turns(id),
    provider        VARCHAR(20) NOT NULL,  -- anthropic | openai | ollama
    response_text   TEXT,
    tokens_in       INT DEFAULT 0,
    tokens_out      INT DEFAULT 0,
    cache_hit       BOOLEAN DEFAULT FALSE,
    rag_used        BOOLEAN DEFAULT FALSE,
    tools_used      TEXT[],
    duration_ms     BIGINT,
    created_at      TIMESTAMP DEFAULT NOW()
  );
  ```

  **Endpoints novos em ms-ai-data:**
  - [ ] `POST /api/v1/sessions` → cria sessão, retorna `{ sessionId }`
  - [ ] `POST /api/v1/sessions/{id}/turns` → salva turno + respostas
  - [ ] `GET  /api/v1/sessions/{id}/turns` → histórico da sessão (para carregar contexto)
  - [ ] `GET  /api/v1/analytics/tokens` → agrega tokens por provider/dia (para dashboard)
  - [ ] `GET  /api/v1/analytics/cache-savings` → total de tokens economizados por cache_hit=true

  **Como demonstra o valor do cache:**
  - Pergunta "que horas abre o spa?" → turn_response: tokens_in=300, cache_hit=false
  - Mesma pergunta de novo → turn_response: tokens_in=0, cache_hit=true, tokens_out=0
  - Analytics: "cache economizou X tokens nesta sessão"

### GAP-05 — Dashboard de analytics (segunda página) ✅ IMPLEMENTADO

### GAP-06 — Front: visual de modo contexto indistinguível do one-shot
- **Arquivo:** `front/hotel-concierge-dashboard.html`
- **Situação:** T5 e T6 com contexto ativo aparecem com o mesmo separador visual de turnos one-shot — o usuário não sabe visualmente que aquelas perguntas estão encadeadas e que o LLM recebeu o histórico.
- **O que implementar:**
  - [ ] Separador de turno em modo contexto com cor/estilo diferente (ex: borda teal em vez de cinza, label "↳ continuação")
  - [ ] Quando `useContext=true`, adicionar um ícone/badge "ctx" no separador de turno
  - [ ] Primeira pergunta da sessão sempre tem separador normal; as seguintes com contexto mostram indicador de cadeia

### GAP-07 — 401 de Anthropic/OpenAI exibe erro genérico sem indicar que é falta de API key
- **Situação:** `tokens_in=0`, `tokens_out=0`, colunas mostram `⚠ erro na chamada` — sem dizer ao usuário que é só falta de key.
- **Evidência nos logs:** `WARN: LLM call failed for provider anthropic: HTTP 401 - invalid x-api-key`
- **O que implementar:**
  - [ ] Detectar HTTP 401 no SSE event `error` e mostrar mensagem amigável: "provider sem API key configurada"
  - [ ] Badge visual "sem key" na coluna em vez de estado de erro vermelho

### GAP-08 — Race condition: contexto pode não ter resposta do turno anterior
- **Situação:** `turn_responses` são salvas de forma fire-and-forget após o LLM terminar. Se o usuário enviar a próxima pergunta antes do Ollama concluir, o `loadContextHistory` busca o turno anterior mas sem resposta ainda — contexto incompleto.
- **Impacto:** LLM do turno seguinte vê a pergunta anterior mas não a resposta.
- **O que implementar:**
  - [ ] Opção A (simples): front desabilita o botão "Enviar" até todos os providers retornarem `done` — já feito parcialmente mas só enquanto há SSE aberto
  - [ ] Opção B (backend): endpoint `GET /api/v1/sessions/{id}/turns` já retorna respostas — `loadContextHistory` já usa, mas o timing é o problema
  - [ ] Solução prática: no front, só reabilitar o botão após evento `done` de TODOS os providers (comportamento atual) — já resolve na maioria dos casos
- **Decisão de stack:** manter HTML puro (sem React/Next.js) — justificativa abaixo
- **Arquivo:** `front/analytics-dashboard.html` (nova página)
- **Biblioteca:** Chart.js via CDN (sem build step)
- **Conteúdo:**
  - [ ] Gráfico de barras: tokens por provider por sessão
  - [ ] Linha do tempo: tokens acumulados ao longo das perguntas
  - [ ] Card "economia de cache": tokens que teriam sido gastos vs tokens gastos de fato
  - [ ] Tabela de sessões: data, nº de turnos, providers usados, total de tokens
  - [ ] Link de volta para o concierge

### GAP-02 — Training examples nunca são salvos no PostgreSQL (ai_data) ✅ IMPLEMENTADO
- **Arquivo:** `ms-hotel-concierge-ai/.../service/ProviderOrchestrator.java`
- **Situação:** `AiDataClient` declara `saveTrainingExample(body)`, mas nunca é chamado após a resposta do LLM.
- **Impacto:** banco `ai_data` fica vazio — tabela `training_examples` nunca recebe registros.
- **O que implementar:**
  - [ ] Após emitir `done`, chamar `saveTrainingExample({ provider, message, response, toolsUsed, rating: null })`
  - [ ] Fazer em thread separada (fire-and-forget) para não atrasar o SSE

---

## V2 — Bugs e Gaps identificados no teste funcional (2026-07-12)

> Levantamento pós-teste com Ollama (sem API keys de Anthropic/OpenAI).

### BUG-01 — Tool calling via Ollama não executa CRUD do hotel
- **Serviço:** `ms-hotel-concierge-ai` / `ms-hotel-info`
- **Sintoma:** IA (Ollama llama3.2) não consegue cadastrar hóspede nem consultar preços de quartos quando solicitado em linguagem natural. Perguntas sobre informações da pergunta anterior também não transitam para tool calls.
- **Causas prováveis:**
  - `llama3.2` via Ollama não suporta function calling da mesma forma que modelos OpenAI — o formato de tool call pode ser diferente ou precisar de configuração específica
  - `ChatClient.tools(hotelTools)` com `@Tool` da Spring AI pode não estar gerando o schema correto para Ollama
  - Modelo pode não estar retornando a ferramenta no formato esperado — Spring AI pode estar silenciando o erro
- **O que investigar:**
  - [ ] Verificar logs do ms-hotel-concierge-ai durante uma pergunta que deveria acionar tool (ex: "cadastre-me como hóspede")
  - [ ] Checar se o modelo Ollama tem suporte a tools: `ollama show llama3.2` — campo `tools`
  - [ ] Considerar trocar para `llama3.1` ou `qwen2.5` que têm melhor suporte a function calling
  - [ ] Alternativa: detectar intenção no prompt e redirecionar manualmente para o tool sem depender de function calling nativo

### BUG-02 — Front: tokens in/out por mensagem (M header) não atualizam
- **Arquivo:** `front/hotel-concierge-dashboard.html`
- **Sintoma:** o slot `⏳` acima de cada `M` nunca é substituído pelos valores reais `↑X ↓Y ⏱Zs`
- **Causa raiz identificada:** bug de ID duplicado no DOM — `appendMsgHeader` sempre cria um elemento com `id="msg-meta-{providerId}"`. A partir da M2, existem dois elementos com o mesmo ID; `getElementById` retorna sempre o PRIMEIRO (M1), que já foi finalizado, então M2 nunca atualiza.
- **Fix:** substituir `getElementById("msg-meta-${id}")` por referência direta ao elemento — armazenar em `let currentMsgMetaEl = {}` dentro de `appendMsgHeader` e usar esse mapa em `handleEvent(done/error)`.
- **Impacto:** toda visualização de tokens/duração por mensagem fica quebrada.

### GAP-09 — Thread header (T) deve mostrar total agregado de tokens (M1 + M2 + ...)
- **Arquivo:** `front/hotel-concierge-dashboard.html`
- **Situação:** o cabeçalho `T4 · thread com contexto` não exibe nenhuma métrica. O usuário quer ver o custo acumulado da thread inteira.
- **O que implementar:**
  - [ ] Manter um acumulador por thread por provider: `threadTokens[providerId] = { in: 0, out: 0 }`
  - [ ] A cada evento `done`, somar `pendingMetrics[id].tokensIn/Out` no acumulador da thread
  - [ ] Atualizar o elemento do thread header com `↑{totalIn} ↓{totalOut}` após cada `done`
  - [ ] Referência ao elemento do thread header armazenada em variável (não usar getElementById com ID fixo)

### GAP-10 — Coluna deve exibir total acumulado de tokens da sessão inteira
- **Arquivo:** `front/hotel-concierge-dashboard.html`
- **Situação:** a linha `tok-{id}` no topo de cada coluna mostra apenas os tokens do último turno. O usuário quer o total de todas as threads da sessão acumulado.
- **O que implementar:**
  - [ ] Manter `sessionTokens[providerId] = { in: 0, out: 0 }` atualizado a cada `done`
  - [ ] Mostrar na linha de métricas do cabeçalho da coluna: `sessão ↑{totalIn} ↓{totalOut}` (subtexto menor)
  - [ ] Ou substituir o campo `tok-{id}` por dois valores: "última mensagem" e "total sessão"

### GAP-12 — Cache semântico com embeddings (Opção C)
- **Serviços:** `ms-ai-data` (novo endpoint) + `ms-hotel-concierge-ai` (troca do cache check)
- **Motivação:** cache atual usa hash exato da string — "que horas abre o spa?" e "o spa abre que horas?" geram hashes diferentes e desperdiçam tokens. Cache semântico resolve qualquer variação de phrasing e é compartilhado entre todos os usuários/sessões.
- **Como funciona:**
  1. Ao receber uma pergunta, gerar embedding do texto (mesmo modelo já usado no RAG)
  2. Buscar no Redis Vector Store por perguntas cacheadas com similaridade coseno > 0.85
  3. Se encontrar, retornar a resposta armazenada — zero tokens gastos
  4. Se não encontrar, chamar o LLM normalmente e salvar `{ pergunta, embedding, resposta }` no Vector Store
- **Infraestrutura:** Redis Stack com RediSearch já está rodando — é o mesmo usado pelo RAG. Só precisa de um índice/namespace separado para o cache semântico.
- **O que implementar:**
  - [ ] `ms-ai-data`: novo endpoint `POST /api/v1/semantic-cache/search` — recebe `{ query, provider, threshold }`, retorna `{ hit: bool, response? }`
  - [ ] `ms-ai-data`: novo endpoint `POST /api/v1/semantic-cache/store` — recebe `{ query, provider, response }`, gera embedding e indexa
  - [ ] `ms-hotel-concierge-ai` `ProviderOrchestrator`: substituir `safeGetCache(key)` por chamada ao `/semantic-cache/search`; substituir `safePutCache` por chamada ao `/semantic-cache/store`
  - [ ] Manter o cache Redis atual como fallback ou remover após validação
  - [ ] Threshold sugerido: 0.85 (ajustável por config)
- **Impacto esperado:** perguntas semanticamente iguais mas com phrasing diferente → cache HIT → tokens_in=0, tokens_out=0 — demonstrável no dashboard de analytics

### GAP-11 — ms-hotel-info: levantar e documentar regras de negócio
- **Serviço:** `ms-hotel-info`
- **Situação:** o serviço tem endpoints funcionais mas sem validações de regras de negócio — qualquer dado é aceito. O usuário irá definir as regras de domínio em momento posterior.
- **Escopo (a ser refinado pelo usuário):**
  - [ ] Reserva só pode ser feita se hóspede já estiver cadastrado (existência verificada antes de `create_booking`)
  - [ ] Reserva de serviço (spa, restaurante) requer nome dos hóspedes ou ID do hóspede
  - [ ] Validar disponibilidade antes de criar reserva (não double-booking)
  - [ ] Mínimo de antecedência para reserva (ex: não reservar para o passado)
  - [ ] Regras de loyalty tier (SILVER/GOLD/PLATINUM — benefícios a definir)
  - [ ] Capacidade de quartos / serviços (número máximo de pessoas por slot)
  - [ ] Cancelamento de reserva (prazo mínimo, penalidade)
  - [ ] **PENDENTE:** usuário irá escrever as regras de negócio definitivas

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
