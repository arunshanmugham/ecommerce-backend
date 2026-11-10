package com.example.ecommerce.temporal.activities;

import com.example.ecommerce.temporal.model.OrderLine;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

/** Inventory service. Polled on {@link com.example.ecommerce.temporal.TaskQueues#INVENTORY}. */
@ActivityInterface
public interface InventoryActivities {

    @ActivityMethod
    void reserveInventory(String orderNumber, List<OrderLine> items);

    /** Compensation for {@link #reserveInventory}. */
    @ActivityMethod
    void releaseInventory(String orderNumber);
}
