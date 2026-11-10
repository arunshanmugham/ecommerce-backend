package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrderNumber(String orderNumber);
}
