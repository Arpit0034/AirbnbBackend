package com.airBnb.application.AirBnbApp.repository;

import com.airBnb.application.AirBnbApp.entity.Guest;
import com.airBnb.application.AirBnbApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuestRepository extends JpaRepository<Guest,Long> {
    List<Guest> findByUser(User user);
}
