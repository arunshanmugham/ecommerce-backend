package com.example.ecommerce.temporal;

/**
 * Task queue names shared by the workflow (to route activities) and the workers.
 * Each "service" polls its own task queue, so it can be deployed and scaled independently.
 */
public final class TaskQueues {
    public static final String ORDER = "ORDER_TASK_QUEUE";
    public static final String PAYMENT = "PAYMENT_TASK_QUEUE";
    public static final String INVENTORY = "INVENTORY_TASK_QUEUE";
    public static final String SHIPPING = "SHIPPING_TASK_QUEUE";
    public static final String NOTIFICATION = "NOTIFICATION_TASK_QUEUE";

    private TaskQueues() {
    }
}
