# ms-hotel-info — Hotel Data MS

**Porta:** 8081 | **Stack:** Spring Boot 3.5 + Spring Data JPA + PostgreSQL 16 + Flyway | **Java:** 21

## Spec completa
`../docs/specs/ms-hotel-info-spec.md`
`../docs/swagger/ms-hotel-info-openapi.yaml`

## O que este serviço faz

Único serviço com acesso ao PostgreSQL de negócio. Expõe REST para o `ms-hotel-concierge-ai` executar tool calls (sem lógica de IA aqui).

## Entidades JPA a criar
```
Guest        — id, name, email, phone, checkIn, checkOut, roomNumber, loyaltyTier, preferences(JSON)
Room         — id, number, floor, type(STANDARD/DELUXE/SUITE), capacity, pricePerNight, status
ServiceType  — id, name(SPA/RESTAURANT/GYM/ROOM_SERVICE), openTime, closeTime, slotDurationMinutes, pricePerSlot
ServiceBooking — id, guestId, serviceTypeId, scheduledAt, durationMinutes, status, notes
RoomBooking  — id, guestId, roomId, checkIn, checkOut, status, totalPrice
```

## Endpoints REST (alinhados ao swagger)
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

## Estrutura de pacotes a criar (projeto do zero)
```
com.thiago.hotelinfo
├── controller/   GuestController, RoomController, PricingController, BookingController
├── service/      GuestService, RoomService, AvailabilityService, BookingService
├── repository/   (JpaRepository por entidade)
├── model/        (entidades JPA)
├── dto/          (requests/responses alinhados ao swagger)
├── exception/    BookingConflictException → HTTP 409
└── config/       DataInitializer (seed)
```

## Seed de dados (DataInitializer — para demo)
- 20 quartos (mix STANDARD/DELUXE/SUITE)
- 4 ServiceTypes: SPA(9h-21h, 60min, R$150), RESTAURANT(12h-23h, 90min, R$80), GYM(6h-22h), ROOM_SERVICE(24h)
- 1 hóspede demo: "João Silva", quarto 302, GOLD, check-in hoje
- 3 reservas existentes no spa (para demonstrar conflito de horário)

## pom.xml — dependências principais
```xml
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-validation
spring-boot-starter-actuator
postgresql (driver)
flyway-core
lombok
spring-boot-testcontainers (test)
testcontainers:postgresql (test)
```

## application.yml
```yaml
server.port: 8081
spring.datasource.url: jdbc:postgresql://localhost:5432/hotel_info
spring.datasource.username: hotel
spring.datasource.password: hotel
spring.jpa.hibernate.ddl-auto: validate
spring.flyway.enabled: true
spring.flyway.locations: classpath:db/migration
```

## Flyway migrations
- `V1__create_tables.sql` — cria todas as tabelas
- `V2__seed_data.sql` — dados iniciais de demo (ou usar DataInitializer se preferir Java)
