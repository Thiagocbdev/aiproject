Crie um dashboard em React + JavaScript (sem TypeScript, arquivos .jsx) + Tailwind CSS chamado "Hotel Concierge AI — Agentic Demo".

CONTEXTO
É uma demo de apresentação: uma pergunta do usuário é enviada simultaneamente para 3 providers de LLM (Anthropic, OpenAI, Ollama) e cada um responde em uma coluna própria, mostrando em tempo real métricas, ferramentas usadas e a resposta aparecendo progressivamente (efeito streaming).

LAYOUT
- Tema escuro (dark mode) como padrão, fundo quase preto, cards em cinza escuro levemente mais claro que o fundo, sem gradientes ou sombras pesadas.
- Topo: título "Hotel concierge · agentic demo" à esquerda, indicador "N providers ativos" à direita.
- Logo abaixo: uma barra com input de texto (placeholder: "Pergunte algo ao concierge") e botão de enviar.
- Corpo: grid de 3 colunas (uma por provider: Anthropic, OpenAI, Ollama), responsivo (empilha em mobile).

CADA COLUNA (ProviderColumn) DEVE TER, DE CIMA PRA BAIXO:
1. Header: nome do provider + bolinha de status (verde = online, cinza = offline)
2. Linha de métricas em fonte monoespaçada: "X in / Y out" tokens à esquerda, "temp 0.XX" à direita
3. Badges pequenos para: ferramentas/tools chamadas (ex: check_availability, create_booking) e uso de RAG (ex: "rag faq", "rag off")
4. Caixa de log estilo terminal (fonte monoespaçada, fundo levemente diferente do card, texto pequeno) mostrando linhas tipo:
   "> tool_call: create_booking(spa, 18:00)"
   "> aguardando confirmação"
   com um cursor piscando no final quando está processando
5. Lista de "bolhas" de resposta (cada uma um card pequeno arredondado) que vão sendo adicionadas uma a uma conforme a resposta "chega"

COMPORTAMENTO (mock por enquanto, sem chamar LLM real)
- Ao clicar em enviar, cada coluna deve simular um streaming: linhas de log aparecem com delay (setTimeout, ~400-800ms entre linhas), métricas de tokens sobem incrementalmente, e a resposta final aparece como texto sendo "digitado" (efeito typewriter) dentro da bolha de resposta.
- Estruture os dados mockados numa camada de serviço separada (src/services/conciergeService.js), com uma função `askConcierge(message)` que retorna um async generator (ou usa callbacks/eventos) emitindo eventos conforme a resposta "chega", para que depois eu troque o mock por chamadas reais (fetch/EventSource) para um backend Spring Boot sem precisar mexer nos componentes visuais.
- Cada provider deve ter uma "personalidade" de resposta ligeiramente diferente nos mocks (ex: Anthropic mais focado em confirmação de reserva, OpenAI trazendo contexto de RAG/FAQ, Ollama podendo aparecer offline como fallback local).

FORMATO DO ESTADO (objeto JS, comentar a forma esperada em JSDoc se quiser)
Cada provider deve ter um estado com: id, label, online, tokensIn, tokensOut, temperature, ragUsed, toolsUsed (array de strings), logLines (array de strings), responses (array de strings), streaming (boolean).

ESTRUTURA DE PASTAS
src/components (AskBar.jsx, ProviderColumn.jsx, ProviderStatusBadge.jsx, MetricsRow.jsx, ToolBadge.jsx, LiveLog.jsx, ResponseBubble.jsx), src/services (conciergeService.js com mock), src/mocks (conciergeMock.js), src/pages (Dashboard.jsx).

IMPORTANTE
Não implemente nenhuma chamada real a API de LLM. Todo o "backend" é mockado localmente por enquanto — o backend real será um projeto Spring Boot separado que eu vou integrar depois trocando apenas a camada de serviço.
