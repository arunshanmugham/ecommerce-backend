package com.example.ecommerce.temporal.activities;

import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.ShippingService;
import io.temporal.failure.ApplicationFailure;
import org.springframework.stereotype.Component;

@Component
public class ShippingActivitiesImpl implements ShippingActivities {

    private final ShippingService shippingService;

    public ShippingActivitiesImpl(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @Override
    public String shipOrder(String orderNumber, String shippingAddress) {
        try {
            return shippingService.ship(orderNumber, shippingAddress);
        } catch (BusinessException e) {
            throw ApplicationFailure.newNonRetryableFailure(e.getMessage(), e.getType());
        }
    }
}
