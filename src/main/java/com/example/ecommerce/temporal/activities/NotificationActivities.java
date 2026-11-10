package com.example.ecommerce.temporal.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/** Notification service. Polled on {@link com.example.ecommerce.temporal.TaskQueues#NOTIFICATION}. */
@ActivityInterface
public interface NotificationActivities {

    /**
     * @param type a short label such as ORDER_CONFIRMED or ORDER_FAILED; one message per type per order
     */
    @ActivityMethod
    void notifyCustomer(String orderNumber, String email, String type, String message);
}
