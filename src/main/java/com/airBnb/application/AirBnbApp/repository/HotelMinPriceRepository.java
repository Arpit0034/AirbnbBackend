package com.airBnb.application.AirBnbApp.repository;

import com.airBnb.application.AirBnbApp.dto.HotelPriceDto;
import com.airBnb.application.AirBnbApp.entity.Hotel;
import com.airBnb.application.AirBnbApp.entity.HotelMinPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface HotelMinPriceRepository extends JpaRepository<HotelMinPrice,Long> {

    @Query("""
            Select new com.airBnb.application.AirBnbApp.dto.HotelPriceDto(i.hotel,AVG(i.price))
            from HotelMinPrice i
            where i.hotel.city = :city
                and i.date between :startDate and :endDate
                and i.hotel.active = true
            GROUP BY i.hotel
            """)
    Page<HotelPriceDto> findHotelsWithAvailableInventory(
            @Param("city") String city ,
            @Param("startDate") LocalDate startDate ,
            @Param("endDate") LocalDate endDate ,
            @Param("roomsCount") Integer roomsCount ,
            @Param("dateCount") Long dateCount,
            Pageable pageable
    ) ;
    Optional<HotelMinPrice> findByHotelAndDate(Hotel hotel, LocalDate date);
}
