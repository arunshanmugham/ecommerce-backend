package com.example.ecommerce.web.dto;

import com.example.ecommerce.domain.CustomerOrder;
import com.example.ecommerce.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderNumber,
        String workflowId,
        OrderStatus status,
        String customerName,
        String customerEmail,
        String shippingAddress,
        BigDecimal totalAmount,
        String paymentTransactionId,
        String trackingNumber,
        String failureReason,
        List<Line> items,
        Instant createdAt,
        Instant updatedAt) {

    public record Line(String sku, int quantity, BigDecimal unitPrice) {
    }

    public static OrderResponse from(CustomerOrder o) {
        return new OrderResponse(
                o.getOrderNumber(), o.getWorkflowId(), o.getStatus(),
                o.getCustomerName(), o.getCustomerEmail(), o.getShippingAddress(),
                o.getTotalAmount(), o.getPaymentTransactionId(), o.getTrackingNumber(), o.getFailureReason(),
                o.getItems().stream().map(i -> new Line(i.getSku(), i.getQuantity(), i.getUnitPrice())).toList(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
