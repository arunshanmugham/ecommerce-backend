package com.example.ecommerce.web.dto;

import com.example.ecommerce.domain.InventoryReservation;
import com.example.ecommerce.domain.Notification;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.domain.Shipment;

import java.util.List;

/** Everything each service recorded for one order - handy to verify the workflow end to end. */
public record OrderAuditResponse(
        OrderResponse order,
        Payment payment,
        List<InventoryReservation> inventoryReservations,
        Shipment shipment,
        List<Notification> notifications) {
}
