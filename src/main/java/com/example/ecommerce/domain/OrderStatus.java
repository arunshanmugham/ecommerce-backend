package com.example.ecommerce.domain;

public enum OrderStatus {
    PENDING,
    PAYMENT_PROCESSED,
    INVENTORY_RESERVED,
    SHIPPED,
    COMPLETED,
    FAILED
}
