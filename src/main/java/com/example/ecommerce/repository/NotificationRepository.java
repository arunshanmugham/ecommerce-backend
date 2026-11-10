package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByOrderNumberOrderBySentAtAsc(String orderNumber);

    boolean existsByOrderNumberAndType(String orderNumber, String type);
}
