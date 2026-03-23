package com.airBnb.application.AirBnbApp.service;


import com.airBnb.application.AirBnbApp.dto.HotelPriceResponseDto;
import com.airBnb.application.AirBnbApp.dto.HotelSearchRequest;
import com.airBnb.application.AirBnbApp.dto.InventoryDto;
import com.airBnb.application.AirBnbApp.dto.UpdateInventoryRequestDto;
import com.airBnb.application.AirBnbApp.entity.Room;
import org.springframework.data.domain.Page;

import java.util.List;

public interface InventoryService {

    void initializeRoomForAYear(Room room);

    void deleteAllInventories(Room room);

    Page<HotelPriceResponseDto> searchHotels(HotelSearchRequest hotelSearchRequest);

    List<InventoryDto> getAllInventoryByRoom(Long roomId);

    void updateInventory(Long roomId, UpdateInventoryRequestDto updateInventoryRequestDto);
}