package com.airBnb.application.AirBnbApp.repository;

import com.airBnb.application.AirBnbApp.entity.Hotel;
import com.airBnb.application.AirBnbApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotelRepository extends JpaRepository<Hotel,Long> {
    List<Hotel> findByOwner(User user);
}
