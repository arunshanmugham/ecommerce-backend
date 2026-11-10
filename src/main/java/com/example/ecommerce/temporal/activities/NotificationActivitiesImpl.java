package com.example.ecommerce.temporal.activities;

import com.example.ecommerce.service.NotificationService;
import org.springframework.stereotype.Component;

@Component
public class NotificationActivitiesImpl implements NotificationActivities {

    private final NotificationService notificationService;

    public NotificationActivitiesImpl(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void notifyCustomer(String orderNumber, String email, String type, String message) {
        notificationService.send(orderNumber, email, type, message);
    }
}
