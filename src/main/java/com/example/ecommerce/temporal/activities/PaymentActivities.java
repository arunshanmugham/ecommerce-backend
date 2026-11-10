package com.example.ecommerce.temporal.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;

/** Payment service. Polled on {@link com.example.ecommerce.temporal.TaskQueues#PAYMENT}. */
@ActivityInterface
public interface PaymentActivities {

    /** @return the payment transaction id */
    @ActivityMethod
    String processPayment(String orderNumber, BigDecimal amount, String paymentToken);

    /** Compensation for {@link #processPayment}. */
    @ActivityMethod
    void refundPayment(String orderNumber);
}
