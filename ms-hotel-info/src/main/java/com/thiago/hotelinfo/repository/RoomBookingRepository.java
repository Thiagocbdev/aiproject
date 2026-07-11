package com.thiago.hotelinfo.repository;

import com.thiago.hotelinfo.model.RoomBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RoomBookingRepository extends JpaRepository<RoomBooking, UUID> {}
