package com.thiago.hotelinfo.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "service_types")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceType {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "slot_duration_minutes")
    private Integer slotDurationMinutes;

    @Column(name = "price_per_slot", precision = 10, scale = 2)
    private BigDecimal pricePerSlot;
}
