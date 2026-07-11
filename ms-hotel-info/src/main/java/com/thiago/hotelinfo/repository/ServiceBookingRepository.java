package com.thiago.hotelinfo.repository;

import com.thiago.hotelinfo.model.ServiceBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, UUID> {
    List<ServiceBooking> findByGuestId(UUID guestId);

    @Query("SELECT sb FROM ServiceBooking sb WHERE sb.serviceType.id = :serviceTypeId AND sb.scheduledAt BETWEEN :start AND :end AND sb.status != 'CANCELLED'")
    List<ServiceBooking> findConflicting(
        @Param("serviceTypeId") UUID serviceTypeId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
