package com.airBnb.application.AirBnbApp.strategy;

import com.airBnb.application.AirBnbApp.entity.Inventory;

import java.math.BigDecimal;

public class BasePricingStrategy implements PricingStrategy {

    @Override
    public BigDecimal calculatePrice(Inventory inventory) {
        return inventory.getRoom().getBasePrice() ;
    }
}
