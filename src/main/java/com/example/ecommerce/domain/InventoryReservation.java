package com.example.ecommerce.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_reservations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"orderNumber", "sku"}))
public class InventoryReservation {

    public enum Status { RESERVED, RELEASED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String orderNumber;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected InventoryReservation() {
    }

    public InventoryReservation(String orderNumber, String sku, int quantity) {
        this.orderNumber = orderNumber;
        this.sku = sku;
        this.quantity = quantity;
        this.status = Status.RESERVED;
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
