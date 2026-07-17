# ms-hotel-info — Hotel Data Service

**Porta:** 8081 | **Stack:** Spring Boot 3.5 · Spring Data JPA · PostgreSQL 16 · Flyway | **Java:** 21

## Responsabilidade

Único serviço com acesso direto ao PostgreSQL de negócio. Expõe REST para o `ms-hotel-concierge-ai`
executar tool calls. Sem lógica de IA — dados e regras de domínio do hotel.

## Documentação completa

- `../docs/specs/ms-hotel-info-spec.md`
- `../docs/swagger/ms-hotel-info-openapi.yaml`

## Modelo de domínio

| Entidade | Descrição |
|---|---|
| `Guest` | Hóspede: nome, email, telefone, check-in/out, quarto, tier de fidelidade, preferências (JSON) |
| `Room` | Quarto: número, andar, tipo (STANDARD/DELUXE/SUITE), capacidade, diária, status |
| `ServiceType` | Tipo de serviço: SPA / RESTAURANT / GYM / ROOM_SERVICE com horários e slots |
| `ServiceBooking` | Reserva de serviço: hóspede, tipo, data/hora, duração, status |
| `RoomBooking` | Reserva de quarto: hóspede, quarto, check-in/out, status, total |

## Endpoints

```
GET  POST         /api/v1/guests
GET               /api/v1/guests/{guestId}
GET               /api/v1/rooms
GET               /api/v1/rooms/{roomType}/availability  ?date=&time=
GET               /api/v1/pricing  ?roomType=&date=
GET  POST         /api/v1/bookings  ?guestId=
GET  PATCH        /api/v1/bookings/{bookingId}
GET               /actuator/health
```

## Pacotes

```
com.thiago.hotelinfo
├── controller/   GuestController, RoomController, PricingController, BookingController
├── service/      GuestService, RoomService, AvailabilityService, BookingService
├── repository/   JpaRepository por entidade
├── model/        Entidades JPA
├── dto/          Requests/responses alinhados ao swagger
├── exception/    BookingConflictException → HTTP 409
└── config/       DataInitializer (seed de demo)
```

## Banco de dados

Flyway gerencia o schema:
- `V1__create_tables.sql` — criação das tabelas
- `V2__seed_data.sql` — dados de demo: 20 quartos, 4 tipos de serviço, 1 hóspede, 3 reservas no spa

## Configuração

```yaml
server.port: 8081
spring.datasource.url: ${DB_URL:jdbc:postgresql://localhost:5432/hotel_info}
spring.datasource.username: ${DB_USER:hotel}
spring.datasource.password: ${DB_PASS:hotel}
spring.jpa.hibernate.ddl-auto: validate
spring.flyway.enabled: true
```

## Dependências principais

`spring-boot-starter-web` · `spring-boot-starter-data-jpa` · `spring-boot-starter-validation`
`spring-boot-starter-actuator` · `postgresql` · `flyway-core` · `lombok`
`spring-boot-testcontainers` + `testcontainers:postgresql` (testes)
