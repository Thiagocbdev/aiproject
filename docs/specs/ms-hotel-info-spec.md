# ms-hotel-info — Spec (Hotel Data)

> Versão: 1.0 · Owner: Thiago · Atualizado: 2026-07-11
> Projeto: `ms-hotel-info/` (a criar) · Swagger: `docs/swagger/ms-hotel-info-openapi.yaml`

## 1. Responsabilidade

Único serviço com acesso ao PostgreSQL de negócio. Expõe REST para ser consumido exclusivamente pelo `ms-hotel-concierge-ai` (tool calls via Feign). Sem lógica de IA.

## 2. Stack

| Item | Valor |
|---|---|
| Spring Boot | 3.5 · Java 21 · Porta **8081** |
| Banco | PostgreSQL 16 + Flyway |
| Dependências | `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `lombok`, `postgresql`, `flyway-core` |
| Testes | Testcontainers PostgreSQL |

## 3. Entidades

### Guest
```
id (UUID), name, email, phone
checkIn (date), checkOut (date), roomNumber
loyaltyTier (STANDARD / GOLD / PLATINUM)
preferences (JSONB)
```

### Room
```
id (UUID), number (varchar), floor, type (STANDARD / DELUXE / SUITE)
capacity, pricePerNight (decimal), status (AVAILABLE / OCCUPIED / MAINTENANCE)
```

### ServiceType (catálogo: spa, restaurante, academia, room service)
```
id (UUID), name, description
openTime (time), closeTime (time), slotDurationMinutes
pricePerSlot (decimal)
```

### ServiceBooking
```
id (UUID), guestId → Guest, serviceTypeId → ServiceType
scheduledAt (timestamp), durationMinutes
status (PENDING / CONFIRMED / CANCELLED / COMPLETED), notes
```

### RoomBooking
```
id (UUID), guestId → Guest, roomId → Room
checkIn (date), checkOut (date)
status (PENDING / CONFIRMED / CHECKED_IN / CHECKED_OUT / CANCELLED)
totalPrice (decimal)
```

## 4. Endpoints REST (conforme swagger `ms-hotel-info-openapi.yaml`)

### Guests
```
GET  /api/v1/guests              ?search={name|email}
POST /api/v1/guests
GET  /api/v1/guests/{guestId}
```

### Rooms
```
GET  /api/v1/rooms
GET  /api/v1/rooms/{roomType}/availability  ?date=&time=
```

### Pricing
```
GET  /api/v1/pricing  ?roomType=&date=
```

### Bookings (usado pelas tools check_availability, create_booking)
```
GET  /api/v1/bookings            ?guestId=
POST /api/v1/bookings            → 201 | 409 (conflito de horário)
GET  /api/v1/bookings/{id}
PATCH /api/v1/bookings/{id}      { status, date, time }
```

## 5. Dados de seed (demo)

`DataInitializer` popula na primeira startup:

- 20 quartos (mix STANDARD/DELUXE/SUITE)
- 4 tipos de serviço:
  - SPA: 09h–21h, slots de 60min, R$150/slot
  - RESTAURANT: 12h–23h, slots de 90min, R$80/pessoa
  - GYM: 06h–22h, livre (sem slot), R$0
  - ROOM_SERVICE: 24h, sem slot, preço variável
- 1 hóspede demo: "João Silva", quarto 302, GOLD, check-in hoje
- 3 reservas existentes no spa (para demonstrar conflito de horário e lista)

## 6. Estrutura de pacotes

```
com.thiago.hotelinfo
├── controller
│   ├── GuestController.java
│   ├── RoomController.java
│   ├── PricingController.java
│   └── BookingController.java
├── service
│   ├── GuestService.java
│   ├── RoomService.java
│   ├── AvailabilityService.java
│   └── BookingService.java
├── repository
│   ├── GuestRepository.java
│   ├── RoomRepository.java
│   ├── ServiceTypeRepository.java
│   ├── ServiceBookingRepository.java
│   └── RoomBookingRepository.java
├── model
│   ├── Guest.java
│   ├── Room.java
│   ├── ServiceType.java
│   ├── ServiceBooking.java
│   └── RoomBooking.java
├── dto
│   └── (requests/responses por endpoint — alinhar com swagger)
├── exception
│   └── BookingConflictException.java   # → HTTP 409
└── config
    └── DataInitializer.java
```

## 7. application.yml

```yaml
server:
  port: 8081

spring:
  application:
    name: ms-hotel-info
  datasource:
    url: jdbc:postgresql://localhost:5432/hotel_info
    username: hotel
    password: hotel
  jpa:
    hibernate.ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
```

## 8. Docker Compose (dev)

```yaml
postgres:
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: hotel_info
    POSTGRES_USER: hotel
    POSTGRES_PASSWORD: hotel
  ports:
    - "5432:5432"
```
