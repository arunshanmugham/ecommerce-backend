package com.example.ecommerce.service;

import com.example.ecommerce.domain.Notification;
import com.example.ecommerce.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulated customer notifications: messages are logged and stored in the notifications table.
 * Replace the body of {@link #send} with a real email/SMS provider call.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /** Sends one notification of the given type per order (retries do not send duplicates). */
    @Transactional
    public void send(String orderNumber, String recipient, String type, String message) {
        if (notifications.existsByOrderNumberAndType(orderNumber, type)) {
            return;
        }
        notifications.save(new Notification(orderNumber, recipient, type, message));
        log.info("EMAIL to {} [{}]: {}", recipient, type, message);
    }
}
