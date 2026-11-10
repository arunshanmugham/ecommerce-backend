package com.example.ecommerce.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderNumber;

    @Column(nullable = false, length = 64)
    private String trackingNumber;

    @Column(nullable = false, length = 32)
    private String carrier;

    @Column(nullable = false, length = 512)
    private String shippingAddress;

    @Column(nullable = false)
    private Instant shippedAt = Instant.now();

    protected Shipment() {
    }

    public Shipment(String orderNumber, String trackingNumber, String carrier, String shippingAddress) {
        this.orderNumber = orderNumber;
        this.trackingNumber = trackingNumber;
        this.carrier = carrier;
        this.shippingAddress = shippingAddress;
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getCarrier() { return carrier; }
    public String getShippingAddress() { return shippingAddress; }
    public Instant getShippedAt() { return shippedAt; }
}
