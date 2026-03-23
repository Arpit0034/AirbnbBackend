package com.airBnb.application.AirBnbApp.dto;

import com.airBnb.application.AirBnbApp.entity.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingStatusResponseDto {
    private BookingStatus bookingStatus;
}
