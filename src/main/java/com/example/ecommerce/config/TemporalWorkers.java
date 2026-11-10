package com.example.ecommerce.config;

import com.example.ecommerce.temporal.TaskQueues;
import com.example.ecommerce.temporal.activities.InventoryActivitiesImpl;
import com.example.ecommerce.temporal.activities.NotificationActivitiesImpl;
import com.example.ecommerce.temporal.activities.OrderActivitiesImpl;
import com.example.ecommerce.temporal.activities.PaymentActivitiesImpl;
import com.example.ecommerce.temporal.activities.ShippingActivitiesImpl;
import com.example.ecommerce.temporal.workflow.OrderWorkflowImpl;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Registers one worker per service task queue and starts polling once the app is ready.
 * Set {@code temporal.workers} (env WORKERS) to host only some services in this process.
 */
@Component
public class TemporalWorkers {

    private static final Logger log = LoggerFactory.getLogger(TemporalWorkers.class);

    private final WorkerFactory factory;
    private final TemporalProperties props;
    private final OrderActivitiesImpl orderActivities;
    private final PaymentActivitiesImpl paymentActivities;
    private final InventoryActivitiesImpl inventoryActivities;
    private final ShippingActivitiesImpl shippingActivities;
    private final NotificationActivitiesImpl notificationActivities;

    public TemporalWorkers(WorkerFactory factory, TemporalProperties props,
                           OrderActivitiesImpl orderActivities,
                           PaymentActivitiesImpl paymentActivities,
                           InventoryActivitiesImpl inventoryActivities,
                           ShippingActivitiesImpl shippingActivities,
                           NotificationActivitiesImpl notificationActivities) {
        this.factory = factory;
        this.props = props;
        this.orderActivities = orderActivities;
        this.paymentActivities = paymentActivities;
        this.inventoryActivities = inventoryActivities;
        this.shippingActivities = shippingActivities;
        this.notificationActivities = notificationActivities;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (enabled("order")) {
            Worker w = factory.newWorker(TaskQueues.ORDER);
            w.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
            w.registerActivitiesImplementations(orderActivities);
        }
        if (enabled("payment")) {
            factory.newWorker(TaskQueues.PAYMENT).registerActivitiesImplementations(paymentActivities);
        }
        if (enabled("inventory")) {
            factory.newWorker(TaskQueues.INVENTORY).registerActivitiesImplementations(inventoryActivities);
        }
        if (enabled("shipping")) {
            factory.newWorker(TaskQueues.SHIPPING).registerActivitiesImplementations(shippingActivities);
        }
        if (enabled("notification")) {
            factory.newWorker(TaskQueues.NOTIFICATION).registerActivitiesImplementations(notificationActivities);
        }
        factory.start();
        log.info("Temporal workers started at {} (namespace {}) for services {}",
                props.target(), props.namespace(), props.workers());
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        factory.shutdown();
        factory.awaitTermination(10, TimeUnit.SECONDS);
    }

    private boolean enabled(String service) {
        return props.workers() != null
                && props.workers().stream().map(String::trim).anyMatch(service::equalsIgnoreCase);
    }
}
