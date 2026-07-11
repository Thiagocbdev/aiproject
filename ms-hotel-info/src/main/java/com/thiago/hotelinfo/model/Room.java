package com.thiago.hotelinfo.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String number;

    private Integer floor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type;

    private Integer capacity;

    @Column(name = "price_per_night", precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RoomStatus status = RoomStatus.AVAILABLE;

    public enum RoomType { STANDARD, DELUXE, SUITE }
    public enum RoomStatus { AVAILABLE, OCCUPIED, MAINTENANCE }
}
