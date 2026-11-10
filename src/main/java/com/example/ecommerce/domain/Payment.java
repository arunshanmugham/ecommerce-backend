package com.example.ecommerce.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

    public enum Status { CHARGED, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderNumber;

    @Column(nullable = false, unique = true, length = 64)
    private String transactionId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant refundedAt;

    protected Payment() {
    }

    public Payment(String orderNumber, String transactionId, BigDecimal amount) {
        this.orderNumber = orderNumber;
        this.transactionId = transactionId;
        this.amount = amount;
        this.status = Status.CHARGED;
    }

    public void markRefunded() {
        this.status = Status.REFUNDED;
        this.refundedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getTransactionId() { return transactionId; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRefundedAt() { return refundedAt; }
}
