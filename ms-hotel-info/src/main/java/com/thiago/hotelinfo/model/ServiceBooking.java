package com.thiago.hotelinfo.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "service_bookings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    private String notes;

    public enum BookingStatus { PENDING, CONFIRMED, CANCELLED, COMPLETED }
}
