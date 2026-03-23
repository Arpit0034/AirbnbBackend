package com.airBnb.application.AirBnbApp.repository;

import com.airBnb.application.AirBnbApp.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room,Long> {
}
