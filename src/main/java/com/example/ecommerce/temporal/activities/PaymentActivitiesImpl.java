package com.example.ecommerce.temporal.activities;

import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.PaymentService;
import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentActivitiesImpl implements PaymentActivities {

    private final PaymentService paymentService;

    public PaymentActivitiesImpl(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public String processPayment(String orderNumber, BigDecimal amount, String paymentToken) {
        // Demo of Temporal retries: "tok_flaky" simulates a gateway outage on the first 2 attempts.
        int attempt = Activity.getExecutionContext().getInfo().getAttempt();
        if ("tok_flaky".equals(paymentToken) && attempt < 3) {
            throw new IllegalStateException("Payment gateway unavailable (attempt " + attempt + ")");
        }
        try {
            return paymentService.charge(orderNumber, amount, paymentToken);
        } catch (BusinessException e) {
            throw ApplicationFailure.newNonRetryableFailure(e.getMessage(), e.getType());
        }
    }

    @Override
    public void refundPayment(String orderNumber) {
        paymentService.refund(orderNumber);
    }
}
