package com.airBnb.application.AirBnbApp.service;

import com.airBnb.application.AirBnbApp.entity.Booking;

public interface CheckOutService {

    String getCheckoutSession(Booking booking, String successUrl, String failureUrl);

}