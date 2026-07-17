# Hotel Grand Vista — Regras de Negócio

## 1. Cadastro de Hóspedes

### 1.1 Dados mínimos obrigatórios
- **Nome**: obrigatório. Aceita nome simples ou nome com sobrenome (mínimo 2 palavras recomendado, mas aceita apenas um nome).
- **E-mail**: recomendado para confirmações, mas não obrigatório no cadastro inicial.
- **Telefone**: recomendado, não obrigatório.

### 1.2 Validações de cadastro
- Nome não pode ser vazio nem ter menos de 2 caracteres.
- E-mail, quando informado, deve ter formato válido (ex: nome@dominio.com).
- Não é permitido dois cadastros com o mesmo e-mail ativo no sistema.
- Número do quarto, quando informado, deve existir na lista de quartos do hotel.

### 1.3 Edição de cadastro
- O hóspede pode atualizar e-mail, telefone e preferências a qualquer momento.
- Nome e número do quarto não podem ser alterados após check-in sem aprovação da recepção.

---

## 2. Reservas — Regra Fundamental

### 2.1 Hóspede deve estar cadastrado ANTES de qualquer reserva
- **REGRA ABSOLUTA**: toda reserva (quarto ou serviço) exige que o hóspede esteja previamente cadastrado.
- Se o hóspede solicitar reserva sem cadastro, o concierge deve:
  1. Informar que o cadastro é necessário.
  2. Coletar nome (obrigatório) e e-mail/telefone (opcionais) para cadastrar.
  3. Após cadastro confirmado, prosseguir com a reserva.
- Nunca assumir que um nome mencionado corresponde a um cadastro existente — sempre verificar.

### 2.2 Confirmação obrigatória antes de criar reserva
- O concierge NUNCA cria uma reserva sem confirmação explícita do hóspede.
- Fluxo obrigatório:
  1. Apresentar resumo da reserva (serviço, data, horário, valor).
  2. Aguardar confirmação explícita ("sim", "confirmar", "pode fazer", "ok").
  3. Somente após confirmação, executar `create_booking`.
- Resposta ambígua ou dúvida → perguntar novamente antes de prosseguir.

---

## 3. Reservas de Quarto (Room Booking)

- Check-in: a partir das 14h00.
- Check-out: até as 12h00.
- Período mínimo: 1 diária; período máximo: 30 diárias.
- Quarto deve estar disponível no período solicitado — sem double-booking.
- Criança até 6 anos: gratuita (mesmo quarto dos pais, sem cama adicional).
- Criança de 7 a 12 anos: 50% da tarifa de adulto.
- Reservas para datas passadas não são permitidas.

---

## 4. Reservas de Serviços (Spa, Restaurante, Academia, Room Service)

- Hóspede deve estar ativo no hotel (check-in realizado, check-out não ocorrido).
- Horários sujeitos à disponibilidade de slots — consultar `check_availability` antes de sugerir.
- Não é permitido reservar dois serviços no mesmo horário para o mesmo hóspede.
- Cancelamento com menos de 48h de antecedência: multa equivalente ao valor do serviço.
- No-show (não comparecimento sem cancelamento): cobrança integral.

### 4.1 Capacidade máxima por serviço
- **Restaurante**: máximo 80 pessoas por slot de 90 minutos (12h–23h).
- **Spa**: máximo 10 pessoas por slot de 60 minutos (9h–21h); 5 salas, 2 por sala.
- **Academia**: máximo 30 pessoas simultâneas (6h–23h); sem reserva, livre acesso.
- **Room Service**: disponível 24 horas; sem limite de capacidade.

---

## 5. Cancelamento e Políticas Gerais

- Cancelamento com 48h+ de antecedência: sem multa.
- Cancelamento com menos de 48h: multa de 1 diária ou 100% do valor do serviço.
- No-show: 100% da primeira diária cobrada (para quartos) ou 100% do serviço.
- Pré-autorização no check-in: valor de 1 diária como garantia no cartão de crédito.

---

## 6. Programa de Fidelidade

| Tier      | Estadias | Benefício principal                          |
|-----------|----------|----------------------------------------------|
| STANDARD  | 1ª–2ª   | Sem desconto adicional                        |
| SILVER    | 3ª–6ª   | 10% de desconto em serviços                   |
| GOLD      | 7ª–14ª  | 20% de desconto + early check-in gratuito     |
| PLATINUM  | 15ª+    | 30% de desconto + late check-out + upgrade    |

- Tier avaliado automaticamente após cada check-out.
- O concierge informa o tier atual ao hóspede quando perguntado.

---

## 7. Responsabilidades do Concierge (IA)

- **Verificar disponibilidade** via `check_availability` ANTES de sugerir horário.
- **Consultar preços** via `get_price` — nunca inventar valores.
- **Verificar cadastro** via `get_guest_profile` antes de qualquer reserva.
- **Cadastrar hóspede** se ainda não cadastrado (coletar nome mínimo).
- **Aguardar confirmação** explícita antes de chamar `create_booking`.
- **Registrar toda interação** para análise futura de qualidade.
- Responder sempre em português brasileiro, com clareza e objetividade.
