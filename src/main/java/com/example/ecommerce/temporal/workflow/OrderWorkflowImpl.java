package com.example.ecommerce.temporal.workflow;

import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.temporal.TaskQueues;
import com.example.ecommerce.temporal.activities.InventoryActivities;
import com.example.ecommerce.temporal.activities.NotificationActivities;
import com.example.ecommerce.temporal.activities.OrderActivities;
import com.example.ecommerce.temporal.activities.PaymentActivities;
import com.example.ecommerce.temporal.activities.ShippingActivities;
import com.example.ecommerce.temporal.model.OrderDetails;
import com.example.ecommerce.temporal.model.OrderProgress;
import com.example.ecommerce.temporal.model.OrderResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Workflow code must be deterministic: no I/O, no system time, no random. All side effects
 * happen in activities, each routed to the task queue of the service that owns it.
 */
public class OrderWorkflowImpl implements OrderWorkflow {

    private static final Logger log = Workflow.getLogger(OrderWorkflowImpl.class);

    private static final RetryOptions RETRY = RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(1))
            .setBackoffCoefficient(2.0)
            .setMaximumInterval(Duration.ofSeconds(30))
            .setMaximumAttempts(5)
            .build();

    private static ActivityOptions optionsFor(String taskQueue) {
        return ActivityOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RETRY)
                .build();
    }

    private final PaymentActivities payment =
            Workflow.newActivityStub(PaymentActivities.class, optionsFor(TaskQueues.PAYMENT));
    private final InventoryActivities inventory =
            Workflow.newActivityStub(InventoryActivities.class, optionsFor(TaskQueues.INVENTORY));
    private final ShippingActivities shipping =
            Workflow.newActivityStub(ShippingActivities.class, optionsFor(TaskQueues.SHIPPING));
    private final NotificationActivities notification =
            Workflow.newActivityStub(NotificationActivities.class, optionsFor(TaskQueues.NOTIFICATION));
    private final OrderActivities orders =
            Workflow.newActivityStub(OrderActivities.class, optionsFor(TaskQueues.ORDER));

    private final OrderProgress progress = new OrderProgress();

    @Override
    public OrderResult processOrder(OrderDetails order) {
        String orderNumber = order.getOrderNumber();
        Saga saga = new Saga(new Saga.Options.Builder().build());
        String transactionId = null;

        try {
            // 1. Payment. The refund is registered first so it also runs if the charge went
            //    through but the activity timed out; refund is a no-op when nothing was charged.
            step("PROCESSING_PAYMENT");
            saga.addCompensation(payment::refundPayment, orderNumber);
            transactionId = payment.processPayment(orderNumber, order.getTotalAmount(), order.getPaymentToken());
            reached(orderNumber, OrderStatus.PAYMENT_PROCESSED, transactionId, null,
                    "Payment processed, transaction " + transactionId);

            // 2. Inventory check + reservation.
            step("RESERVING_INVENTORY");
            saga.addCompensation(inventory::releaseInventory, orderNumber);
            inventory.reserveInventory(orderNumber, order.getItems());
            reached(orderNumber, OrderStatus.INVENTORY_RESERVED, null, null, "Inventory reserved");

            // 3. Shipping.
            step("SHIPPING");
            String trackingNumber = shipping.shipOrder(orderNumber, order.getShippingAddress());
            reached(orderNumber, OrderStatus.SHIPPED, null, trackingNumber,
                    "Shipped, tracking " + trackingNumber);

            // 4. Notify the customer. The order is already fulfilled, so a notification
            //    failure is logged rather than undoing payment and shipment.
            step("NOTIFYING_CUSTOMER");
            notifyQuietly(order, "ORDER_CONFIRMED",
                    "Hi " + order.getCustomerName() + ", your order " + orderNumber + " has shipped. "
                            + "Tracking number: " + trackingNumber + ".");

            reached(orderNumber, OrderStatus.COMPLETED, null, null, "Order completed");
            step("DONE");
            return new OrderResult(orderNumber, OrderStatus.COMPLETED, transactionId, trackingNumber,
                    "Order completed");

        } catch (ActivityFailure e) {
            String reason = rootMessage(e);
            log.warn("Order {} failed at {}: {}", orderNumber, progress.getCurrentStep(), reason);
            progress.getHistory().add("FAILED at " + progress.getCurrentStep() + ": " + reason);

            step("COMPENSATING");
            saga.compensate();
            progress.getHistory().add("Compensations completed (inventory released / payment refunded)");

            progress.setStatus(OrderStatus.FAILED);
            orders.updateOrder(orderNumber, OrderStatus.FAILED, null, null, reason);
            notifyQuietly(order, "ORDER_FAILED",
                    "Hi " + order.getCustomerName() + ", we could not complete your order " + orderNumber
                            + ": " + reason + ". Any payment has been refunded.");
            step("DONE");
            return new OrderResult(orderNumber, OrderStatus.FAILED, transactionId, null, reason);
        }
    }

    @Override
    public OrderProgress getProgress() {
        return progress;
    }

    private void step(String name) {
        progress.setCurrentStep(name);
    }

    private void reached(String orderNumber, OrderStatus status, String transactionId,
                         String trackingNumber, String message) {
        orders.updateOrder(orderNumber, status, transactionId, trackingNumber, null);
        progress.setStatus(status);
        progress.getHistory().add(message);
    }

    private void notifyQuietly(OrderDetails order, String type, String message) {
        try {
            notification.notifyCustomer(order.getOrderNumber(), order.getCustomerEmail(), type, message);
            progress.getHistory().add("Customer notified (" + type + ")");
        } catch (ActivityFailure e) {
            log.error("Could not notify customer for order {}: {}", order.getOrderNumber(), rootMessage(e));
            progress.getHistory().add("Customer notification failed: " + rootMessage(e));
        }
    }

    private static String rootMessage(ActivityFailure e) {
        Throwable cause = e.getCause();
        if (cause instanceof ApplicationFailure af) {
            return af.getOriginalMessage();
        }
        return cause != null ? cause.getMessage() : e.getMessage();
    }
}
