package com.thiago.hotelinfo.service;

import com.thiago.hotelinfo.dto.RoomResponse;
import com.thiago.hotelinfo.model.Room;
import com.thiago.hotelinfo.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository roomRepository;

    public List<RoomResponse> listRooms(String type) {
        List<Room> rooms;
        if (type != null && !type.isBlank()) {
            try {
                Room.RoomType roomType = Room.RoomType.valueOf(type.toUpperCase());
                rooms = roomRepository.findByType(roomType);
            } catch (IllegalArgumentException e) {
                rooms = roomRepository.findAll();
            }
        } else {
            rooms = roomRepository.findAll();
        }
        return rooms.stream().map(this::toResponse).toList();
    }

    private RoomResponse toResponse(Room r) {
        return new RoomResponse(
            r.getNumber(),
            r.getType() != null ? r.getType().name() : null,
            r.getFloor(),
            r.getCapacity(),
            r.getPricePerNight(),
            r.getStatus() != null ? r.getStatus().name() : null
        );
    }
}
