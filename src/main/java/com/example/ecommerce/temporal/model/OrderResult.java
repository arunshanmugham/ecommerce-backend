package com.example.ecommerce.temporal.model;

import com.example.ecommerce.domain.OrderStatus;

/** Workflow result. */
public class OrderResult {

    private String orderNumber;
    private OrderStatus status;
    private String transactionId;
    private String trackingNumber;
    private String message;

    public OrderResult() {
    }

    public OrderResult(String orderNumber, OrderStatus status, String transactionId,
                       String trackingNumber, String message) {
        this.orderNumber = orderNumber;
        this.status = status;
        this.transactionId = transactionId;
        this.trackingNumber = trackingNumber;
        this.message = message;
    }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
