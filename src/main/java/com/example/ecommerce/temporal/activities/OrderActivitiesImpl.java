package com.example.ecommerce.temporal.activities;

import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.service.OrderService;
import org.springframework.stereotype.Component;

@Component
public class OrderActivitiesImpl implements OrderActivities {

    private final OrderService orderService;

    public OrderActivitiesImpl(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void updateOrder(String orderNumber, OrderStatus status, String transactionId,
                            String trackingNumber, String failureReason) {
        orderService.updateOrder(orderNumber, status, transactionId, trackingNumber, failureReason);
    }
}
