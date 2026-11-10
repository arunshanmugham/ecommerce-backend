package com.example.ecommerce.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String orderNumber;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 2048)
    private String message;

    @Column(nullable = false)
    private Instant sentAt = Instant.now();

    protected Notification() {
    }

    public Notification(String orderNumber, String recipient, String type, String message) {
        this.orderNumber = orderNumber;
        this.recipient = recipient;
        this.type = type;
        this.message = message;
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getRecipient() { return recipient; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public Instant getSentAt() { return sentAt; }
}
