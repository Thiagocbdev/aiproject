# ms-ai-data

Microserviço de dados IA — RAG (busca semântica), cache de respostas LLM e dados de treino. Consumido exclusivamente pelo `ms-hotel-concierge-ai` via Feign.

**Porta:** 8082 | **Stack:** Spring Boot 3.5 · Java 21 · Spring AI 1.1.8 · Redis Stack · PostgreSQL 16

---

## Endpoints

### Health
| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/actuator/health` | Estado do serviço |

### RAG — Vector Store `/api/v1/vectors`
| Método | Path | Descrição |
|--------|------|-----------|
| POST | `/api/v1/vectors/index` | Indexa um documento no vector store |
| POST | `/api/v1/vectors/search` | Busca semântica (retorna chunks relevantes) |
| DELETE | `/api/v1/vectors/{id}` | Remove um documento do índice |

Body `POST /api/v1/vectors/search`:
```json
{ "query": "horário do spa", "topK": 3 }
```
Resposta: lista de chunks com `id`, `content`, `score`, `metadata`.

Body `POST /api/v1/vectors/index`:
```json
{ "id": "doc-001", "content": "O spa abre às 9h...", "metadata": { "source": "spa-faq" } }
```

### Cache `/api/v1/cache`
| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/api/v1/cache/{key}` | Obtém entrada (404 se não existir) |
| PUT | `/api/v1/cache/{key}` | Guarda entrada com TTL |
| DELETE | `/api/v1/cache/{key}` | Remove entrada |

Body `PUT /api/v1/cache/{key}`:
```json
{ "response": "texto da resposta", "provider": "anthropic", "ttlSeconds": 172800 }
```
O TTL padrão é **172800s (48h)**. A chave de cache é `{provider}:{hash(pergunta)}`.

### Sessões e Contexto `/api/v1/sessions`
| Método | Path | Descrição |
|--------|------|-----------|
| POST | `/api/v1/sessions/{sessionId}` | Cria/garante que a sessão existe |
| POST | `/api/v1/sessions/{sessionId}/turns` | Cria um turno (pergunta) na sessão |
| POST | `/api/v1/sessions/{sessionId}/turns/{turnId}/responses` | Guarda resposta de um provider para o turno |
| GET | `/api/v1/sessions/{sessionId}/turns` | Obtém histórico da sessão (usado para contexto de conversa) |

### Exemplos de Treino `/api/v1/training`
| Método | Path | Params | Descrição |
|--------|------|--------|-----------|
| GET | `/api/v1/training/examples` | `?limit=50&provider=anthropic` | Lista exemplos |
| POST | `/api/v1/training/examples` | body: `TrainingExampleInput` | Regista exemplo |

### Analytics `/api/v1/analytics`
| Método | Path | Params | Descrição |
|--------|------|--------|-----------|
| GET | `/api/v1/analytics/tokens` | `?days=30` | Tokens consumidos por provider/dia |
| GET | `/api/v1/analytics/cache-savings` | — | Chamadas LLM evitadas pelo cache |

---

## Documentos RAG

Os seguintes ficheiros são indexados automaticamente na startup (`rag.re-index-on-startup: true`):

| Ficheiro | Conteúdo |
|----------|----------|
| `src/main/resources/rag/hotel-manual.md` | Políticas, horários, contactos, regras gerais |
| `src/main/resources/rag/spa-faq.md` | Serviços de spa, preços, reservas, cancelamentos |
| `src/main/resources/rag/restaurant-menu.md` | Cardápio, horários, reservas, opções vegetarianas |
| `src/main/resources/rag/local-attractions.md` | Atracções locais, distâncias, dicas |
| `src/main/resources/rag/business-rules.md` | Regras de negócio, políticas de cancelamento |

- **Chunking:** 512 tokens, 50 overlap
- **Embedding:** `nomic-embed-text` via Ollama (768 dimensões)
- **Índice Redis:** `hotel-knowledge` (prefix `hotel:doc:`)
- **Threshold de similaridade:** 0.70

---

## Correr localmente

Pré-requisitos: PostgreSQL em `localhost:5432` + Redis Stack em `localhost:6379` + Ollama em `localhost:11434` com `nomic-embed-text` instalado.

```bash
# Infra em Docker
docker compose up -d postgres redis

# Garantir que o Ollama tem o modelo de embedding
ollama pull nomic-embed-text

# Iniciar o MS
.\run-local.ps1 ai-data
# ou
cd ms-ai-data && mvn spring-boot:run
```

**Variáveis de ambiente** (opcionais — defaults para Docker local):
```
DB_URL=jdbc:postgresql://localhost:5432/ai_data
DB_USER=hotel
DB_PASS=hotel
REDIS_URI=redis://localhost:6379
REDIS_HOST=localhost
REDIS_PORT=6379
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_EMBED_MODEL=nomic-embed-text
```

---

## Docker

```bash
docker compose up -d ms-ai-data
```

O container usa `host.docker.internal:11434` para chegar ao Ollama nativo do Windows.
