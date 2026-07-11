package com.thiago.hotelinfo.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "room_bookings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoomBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RoomBookingStatus status = RoomBookingStatus.PENDING;

    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice;

    public enum RoomBookingStatus { PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED }
}
