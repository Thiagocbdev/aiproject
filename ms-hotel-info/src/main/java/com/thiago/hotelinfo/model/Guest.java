package com.thiago.hotelinfo.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "guests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Guest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(name = "check_in")
    private LocalDate checkIn;

    @Column(name = "check_out")
    private LocalDate checkOut;

    @Column(name = "room_number")
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "loyalty_tier")
    @Builder.Default
    private LoyaltyTier loyaltyTier = LoyaltyTier.STANDARD;

    public enum LoyaltyTier { STANDARD, GOLD, PLATINUM }
}
