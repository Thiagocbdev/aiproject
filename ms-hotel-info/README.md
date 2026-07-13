# ms-hotel-info

Microserviço de dados do hotel — hóspedes, quartos, reservas e preços. Único serviço com acesso à base de dados PostgreSQL de negócio. Consumido exclusivamente pelo `ms-hotel-concierge-ai` via Feign para tool calls da IA.

**Porta:** 8081 | **Stack:** Spring Boot 3.5 · Java 21 · PostgreSQL 16 · Flyway · Spring Data JPA

---

## Endpoints

### Health
| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/actuator/health` | Estado do serviço |

### Hóspedes `/api/v1/guests`
| Método | Path | Params | Descrição |
|--------|------|--------|-----------|
| GET | `/api/v1/guests` | `?search=nome` (opcional) | Lista / busca hóspedes por nome |
| GET | `/api/v1/guests/{guestId}` | UUID | Perfil completo do hóspede |
| POST | `/api/v1/guests` | body: `GuestInput` | Cadastra novo hóspede (201) |

Body `POST /api/v1/guests`:
```json
{
  "name": "Maria Silva",
  "email": "maria@example.com",
  "phone": "+55 11 99999-0000"
}
```

### Quartos `/api/v1/rooms`
| Método | Path | Params | Descrição |
|--------|------|--------|-----------|
| GET | `/api/v1/rooms` | `?type=STANDARD\|DELUXE\|SUITE` (opcional) | Lista quartos |
| GET | `/api/v1/rooms/{serviceType}/availability` | `?date=YYYY-MM-DD&time=HH:mm` | Disponibilidade de serviço |

Valores válidos para `serviceType`: `spa`, `restaurant`, `gym`, `room_service`

### Preços `/api/v1/pricing`
| Método | Path | Params | Descrição |
|--------|------|--------|-----------|
| GET | `/api/v1/pricing` | `serviceType` (obrigatório), `?date=YYYY-MM-DD` | Preço de um serviço |

Valores válidos para `serviceType`: `spa`, `restaurant`, `gym`, `room_service`

### Reservas `/api/v1/bookings`
| Método | Path | Params | Descrição |
|--------|------|--------|-----------|
| GET | `/api/v1/bookings` | `?guestId=UUID` (opcional) | Lista reservas (todas ou por hóspede) |
| POST | `/api/v1/bookings` | body: `BookingInput` | Cria reserva (201) |
| GET | `/api/v1/bookings/{bookingId}` | UUID | Detalhes de uma reserva |
| PATCH | `/api/v1/bookings/{bookingId}` | body: `BookingPatch` | Actualiza status da reserva |

Body `POST /api/v1/bookings`:
```json
{
  "guestId": "uuid-do-hospede",
  "serviceTypeId": "uuid-do-servico",
  "scheduledAt": "2026-07-16T18:00:00",
  "durationMinutes": 60,
  "notes": "Preferência por massagem relaxante"
}
```

Body `PATCH /api/v1/bookings/{id}`:
```json
{ "status": "CONFIRMED" }
```
Status válidos: `CONFIRMED`, `CANCELLED`, `COMPLETED`

---

## Entidades

| Entidade | Campos principais |
|----------|------------------|
| `Guest` | id (UUID), name, email, phone, checkIn, checkOut, roomNumber, loyaltyTier (STANDARD/GOLD/PLATINUM), preferences (JSONB) |
| `Room` | id (UUID), number, floor, type (STANDARD/DELUXE/SUITE), capacity, pricePerNight, status |
| `ServiceType` | id (UUID), name (SPA/RESTAURANT/GYM/ROOM_SERVICE), openTime, closeTime, slotDurationMinutes, pricePerSlot |
| `ServiceBooking` | id (UUID), guestId, serviceTypeId, scheduledAt, durationMinutes, status, notes |

---

## Dados de seed (demo)

A startup carrega automaticamente:
- **20 quartos** (mix STANDARD/DELUXE/SUITE)
- **4 serviços**: SPA (9h-21h, R$150/slot), RESTAURANT (12h-23h, R$80/slot), GYM (6h-22h), ROOM_SERVICE (24h)
- **1 hóspede demo**: João Silva, quarto 302, tier GOLD
- **3 reservas de spa** já existentes (para demonstrar conflitos)

---

## Erros HTTP

| Código | Situação |
|--------|----------|
| 400 | Parâmetros inválidos (data malformada, campo obrigatório em falta) |
| 404 | Hóspede, quarto ou reserva não encontrado |
| 409 | Email duplicado na criação de hóspede / conflito de horário na reserva |
| 500 | Erro interno |

---

## Correr localmente

Pré-requisitos: PostgreSQL em `localhost:5432` (ou Docker) com base `hotel_info`, user/pass `hotel/hotel`.

```bash
# Infra em Docker
docker compose up -d postgres

# Iniciar o MS
cd ms-hotel-info
mvn spring-boot:run
# ou na raiz:
.\run-local.ps1 hotel-info
```

**Variáveis de ambiente** (opcionais — defaults para Docker local):
```
DB_URL=jdbc:postgresql://localhost:5432/hotel_info
DB_USER=hotel
DB_PASS=hotel
```

---

## Docker

```bash
docker compose up -d ms-hotel-info
```

O container usa as variáveis `DB_URL`, `DB_USER`, `DB_PASS` definidas no `docker-compose.yml` apontando para o container `postgres`.
