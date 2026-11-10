package com.example.ecommerce.temporal;

import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.temporal.activities.InventoryActivities;
import com.example.ecommerce.temporal.activities.NotificationActivities;
import com.example.ecommerce.temporal.activities.OrderActivities;
import com.example.ecommerce.temporal.activities.PaymentActivities;
import com.example.ecommerce.temporal.activities.ShippingActivities;
import com.example.ecommerce.temporal.model.OrderDetails;
import com.example.ecommerce.temporal.model.OrderLine;
import com.example.ecommerce.temporal.model.OrderResult;
import com.example.ecommerce.temporal.workflow.OrderWorkflow;
import com.example.ecommerce.temporal.workflow.OrderWorkflowImpl;
import io.temporal.activity.Activity;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real workflow against in-memory fake services, one worker per task queue. */
class OrderWorkflowTest {

    private TestWorkflowEnvironment env;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private volatile boolean outOfStock;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();

        Worker orderWorker = env.newWorker(TaskQueues.ORDER);
        orderWorker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
        orderWorker.registerActivitiesImplementations((OrderActivities) (n, status, tx, tracking, reason) ->
                calls.add("order:" + status));

        env.newWorker(TaskQueues.PAYMENT).registerActivitiesImplementations(new PaymentActivities() {
            @Override
            public String processPayment(String orderNumber, BigDecimal amount, String token) {
                if ("tok_flaky".equals(token) && Activity.getExecutionContext().getInfo().getAttempt() < 3) {
                    throw new IllegalStateException("gateway down");
                }
                if ("tok_declined".equals(token)) {
                    throw ApplicationFailure.newNonRetryableFailure("Card was declined", "PaymentDeclined");
                }
                calls.add("payment:charge");
                return "TXN-1";
            }

            @Override
            public void refundPayment(String orderNumber) {
                calls.add("payment:refund");
            }
        });

        env.newWorker(TaskQueues.INVENTORY).registerActivitiesImplementations(new InventoryActivities() {
            @Override
            public void reserveInventory(String orderNumber, List<OrderLine> items) {
                if (outOfStock) {
                    throw ApplicationFailure.newNonRetryableFailure("Insufficient stock for X", "OutOfStock");
                }
                calls.add("inventory:reserve");
            }

            @Override
            public void releaseInventory(String orderNumber) {
                calls.add("inventory:release");
            }
        });

        env.newWorker(TaskQueues.SHIPPING).registerActivitiesImplementations((ShippingActivities) (n, addr) -> {
            calls.add("shipping:ship");
            return "TRK-1";
        });

        env.newWorker(TaskQueues.NOTIFICATION).registerActivitiesImplementations(
                (NotificationActivities) (n, email, type, msg) -> calls.add("notify:" + type));

        env.start();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    void happyPathRunsAllServicesInOrder() {
        OrderResult result = run("tok_visa");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(result.getTransactionId()).isEqualTo("TXN-1");
        assertThat(result.getTrackingNumber()).isEqualTo("TRK-1");
        assertThat(calls).containsExactly(
                "payment:charge", "order:PAYMENT_PROCESSED",
                "inventory:reserve", "order:INVENTORY_RESERVED",
                "shipping:ship", "order:SHIPPED",
                "notify:ORDER_CONFIRMED", "order:COMPLETED");
    }

    @Test
    void transientPaymentErrorsAreRetried() {
        OrderResult result = run("tok_flaky");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void declinedPaymentFailsOrderWithoutShipping() {
        OrderResult result = run("tok_declined");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getMessage()).isEqualTo("Card was declined");
        assertThat(calls).containsExactly("payment:refund", "order:FAILED", "notify:ORDER_FAILED");
    }

    @Test
    void outOfStockRefundsPayment() {
        outOfStock = true;

        OrderResult result = run("tok_visa");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.getMessage()).isEqualTo("Insufficient stock for X");
        assertThat(calls).containsExactly(
                "payment:charge", "order:PAYMENT_PROCESSED",
                "inventory:release", "payment:refund",
                "order:FAILED", "notify:ORDER_FAILED");
    }

    private OrderResult run(String paymentToken) {
        OrderDetails details = new OrderDetails();
        details.setOrderNumber("ORD-TEST");
        details.setCustomerName("Test");
        details.setCustomerEmail("test@example.com");
        details.setShippingAddress("1 Main St");
        details.setPaymentToken(paymentToken);
        details.setTotalAmount(new BigDecimal("10.00"));
        details.setItems(List.of(new OrderLine("MOUSE-001", 1, new BigDecimal("10.00"))));

        OrderWorkflow wf = env.getWorkflowClient().newWorkflowStub(OrderWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TaskQueues.ORDER).build());
        return wf.processOrder(details);
    }
}
