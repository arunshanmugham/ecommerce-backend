package com.example.ecommerce.service;

import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulated payment gateway. Test tokens:
 * <ul>
 *   <li>{@code tok_declined} - card declined (permanent failure)</li>
 *   <li>{@code tok_insufficient_funds} - insufficient funds (permanent failure)</li>
 *   <li>{@code tok_flaky} - gateway errors on the first attempts, then succeeds (see PaymentActivitiesImpl)</li>
 *   <li>anything else, e.g. {@code tok_visa} - charge succeeds</li>
 * </ul>
 */
@Service
public class PaymentService {

    public static final String PAYMENT_DECLINED = "PaymentDeclined";

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;

    public PaymentService(PaymentRepository payments) {
        this.payments = payments;
    }

    /** Charges the customer. Idempotent per order: a retry returns the existing transaction. */
    @Transactional
    public String charge(String orderNumber, BigDecimal amount, String paymentToken) {
        var existing = payments.findByOrderNumber(orderNumber);
        if (existing.isPresent()) {
            log.info("Payment for order {} already processed: {}", orderNumber, existing.get().getTransactionId());
            return existing.get().getTransactionId();
        }
        if ("tok_declined".equals(paymentToken)) {
            throw new BusinessException(PAYMENT_DECLINED, "Card was declined");
        }
        if ("tok_insufficient_funds".equals(paymentToken)) {
            throw new BusinessException(PAYMENT_DECLINED, "Insufficient funds");
        }
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        payments.save(new Payment(orderNumber, transactionId, amount));
        log.info("Charged {} for order {} (transaction {})", amount, orderNumber, transactionId);
        return transactionId;
    }

    /** Refunds the payment for an order. Safe to call when nothing was charged or it was already refunded. */
    @Transactional
    public void refund(String orderNumber) {
        payments.findByOrderNumber(orderNumber)
                .filter(p -> p.getStatus() == Payment.Status.CHARGED)
                .ifPresent(p -> {
                    p.markRefunded();
                    log.info("Refunded {} for order {} (transaction {})", p.getAmount(), orderNumber, p.getTransactionId());
                });
    }
}
