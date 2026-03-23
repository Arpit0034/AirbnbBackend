package com.airBnb.application.AirBnbApp.service;

import com.airBnb.application.AirBnbApp.dto.HotelDto;
import com.airBnb.application.AirBnbApp.dto.HotelInfoDto;
import com.airBnb.application.AirBnbApp.dto.HotelInfoRequestDto;

import java.util.List;

public interface HotelService {
    HotelDto createNewHotel(HotelDto hotelDto);

    HotelDto getHotelById(Long id);

    HotelDto updateHotelById(Long id, HotelDto hotelDto);

    void deleteHotelById(Long id);

    void activateHotel(Long hotelId);

    HotelInfoDto getHotelInfoById(Long hotelId, HotelInfoRequestDto hotelInfoRequestDto);

    List<HotelDto> getAllHotels();
}
