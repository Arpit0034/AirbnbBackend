package com.airBnb.application.AirBnbApp.strategy;

import com.airBnb.application.AirBnbApp.entity.Inventory;

import java.math.BigDecimal;

public interface PricingStrategy {
    BigDecimal calculatePrice(Inventory inventory) ;
}
