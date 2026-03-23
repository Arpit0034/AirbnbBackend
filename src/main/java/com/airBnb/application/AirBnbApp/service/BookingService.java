package com.airBnb.application.AirBnbApp.service;

import com.airBnb.application.AirBnbApp.dto.BookingDto;
import com.airBnb.application.AirBnbApp.dto.BookingRequest;
import com.airBnb.application.AirBnbApp.dto.HotelReportDto;
import com.airBnb.application.AirBnbApp.entity.enums.BookingStatus;
import com.stripe.model.Event;

import java.time.LocalDate;
import java.util.List;

public interface BookingService {

    BookingDto initialiseBooking(BookingRequest bookingRequest);

    BookingDto addGuests(Long bookingId, List<Long> guestIdList);

    String initiatePayments(Long bookingId);

    void capturePayment(Event event);

    void cancelBooking(Long bookingId);

    BookingStatus getBookingStatus(Long bookingId);

    List<BookingDto> getAllBookingsByHotelId(Long hotelId);

    HotelReportDto getHotelReport(Long hotelId, LocalDate startDate, LocalDate endDate);

    List<BookingDto> getMyBookings();
}
