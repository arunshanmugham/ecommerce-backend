package com.example.ecommerce.service;

import com.example.ecommerce.domain.InventoryReservation;
import com.example.ecommerce.repository.InventoryReservationRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.temporal.model.OrderLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    public static final String OUT_OF_STOCK = "OutOfStock";

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository products;
    private final InventoryReservationRepository reservations;

    public InventoryService(ProductRepository products, InventoryReservationRepository reservations) {
        this.products = products;
        this.reservations = reservations;
    }

    /**
     * Reserves stock for every line, all-or-nothing (one DB transaction).
     * Idempotent per order: if the order already holds reservations, nothing is changed.
     */
    @Transactional
    public void reserve(String orderNumber, List<OrderLine> items) {
        if (!reservations.findByOrderNumber(orderNumber).isEmpty()) {
            log.info("Inventory for order {} already reserved", orderNumber);
            return;
        }
        for (OrderLine line : items) {
            int updated = products.decrementStock(line.getSku(), line.getQuantity());
            if (updated == 0) {
                // Throwing rolls back the decrements already made for earlier lines.
                throw new BusinessException(OUT_OF_STOCK,
                        "Insufficient stock for " + line.getSku() + " (requested " + line.getQuantity() + ")");
            }
            reservations.save(new InventoryReservation(orderNumber, line.getSku(), line.getQuantity()));
        }
        log.info("Reserved inventory for order {}", orderNumber);
    }

    /** Returns reserved stock to the shelf. Safe to call repeatedly or when nothing was reserved. */
    @Transactional
    public void release(String orderNumber) {
        for (InventoryReservation r : reservations.findByOrderNumber(orderNumber)) {
            if (r.getStatus() == InventoryReservation.Status.RESERVED) {
                products.incrementStock(r.getSku(), r.getQuantity());
                r.setStatus(InventoryReservation.Status.RELEASED);
                log.info("Released {} x {} for order {}", r.getQuantity(), r.getSku(), orderNumber);
            }
        }
    }
}
