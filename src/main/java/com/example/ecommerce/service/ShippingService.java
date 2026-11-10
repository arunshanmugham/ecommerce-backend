package com.example.ecommerce.service;

import com.example.ecommerce.domain.Shipment;
import com.example.ecommerce.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Simulated shipping provider. An address containing {@code UNDELIVERABLE} is rejected permanently.
 */
@Service
public class ShippingService {

    public static final String UNDELIVERABLE = "UndeliverableAddress";

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);

    private final ShipmentRepository shipments;

    public ShippingService(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    /** Creates the shipment and returns its tracking number. Idempotent per order. */
    @Transactional
    public String ship(String orderNumber, String shippingAddress) {
        var existing = shipments.findByOrderNumber(orderNumber);
        if (existing.isPresent()) {
            return existing.get().getTrackingNumber();
        }
        if (shippingAddress.toUpperCase().contains("UNDELIVERABLE")) {
            throw new BusinessException(UNDELIVERABLE, "Carrier cannot deliver to: " + shippingAddress);
        }
        String tracking = "TRK-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        shipments.save(new Shipment(orderNumber, tracking, "UPS", shippingAddress));
        log.info("Shipped order {} with tracking {}", orderNumber, tracking);
        return tracking;
    }
}
