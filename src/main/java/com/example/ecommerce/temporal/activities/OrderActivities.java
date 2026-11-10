package com.example.ecommerce.temporal.activities;

import com.example.ecommerce.domain.OrderStatus;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/** Order service: persists order state changes. Polled on {@link com.example.ecommerce.temporal.TaskQueues#ORDER}. */
@ActivityInterface
public interface OrderActivities {

    /** Null arguments leave the corresponding column unchanged. */
    @ActivityMethod
    void updateOrder(String orderNumber, OrderStatus status, String transactionId,
                     String trackingNumber, String failureReason);
}
