package com.example.ecommerce.temporal.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/** Shipping service. Polled on {@link com.example.ecommerce.temporal.TaskQueues#SHIPPING}. */
@ActivityInterface
public interface ShippingActivities {

    /** @return the carrier tracking number */
    @ActivityMethod
    String shipOrder(String orderNumber, String shippingAddress);
}
