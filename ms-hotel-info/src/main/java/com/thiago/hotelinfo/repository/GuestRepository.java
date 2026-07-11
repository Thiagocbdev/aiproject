package com.thiago.hotelinfo.repository;

import com.thiago.hotelinfo.model.Guest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GuestRepository extends JpaRepository<Guest, UUID> {
    @Query("SELECT g FROM Guest g WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(g.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Guest> findByNameOrEmailContaining(@Param("search") String search);
}
