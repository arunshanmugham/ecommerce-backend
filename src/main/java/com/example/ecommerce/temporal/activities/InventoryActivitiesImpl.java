package com.example.ecommerce.temporal.activities;

import com.example.ecommerce.service.BusinessException;
import com.example.ecommerce.service.InventoryService;
import com.example.ecommerce.temporal.model.OrderLine;
import io.temporal.failure.ApplicationFailure;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventoryActivitiesImpl implements InventoryActivities {

    private final InventoryService inventoryService;

    public InventoryActivitiesImpl(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public void reserveInventory(String orderNumber, List<OrderLine> items) {
        try {
            inventoryService.reserve(orderNumber, items);
        } catch (BusinessException e) {
            throw ApplicationFailure.newNonRetryableFailure(e.getMessage(), e.getType());
        }
    }

    @Override
    public void releaseInventory(String orderNumber) {
        inventoryService.release(orderNumber);
    }
}
