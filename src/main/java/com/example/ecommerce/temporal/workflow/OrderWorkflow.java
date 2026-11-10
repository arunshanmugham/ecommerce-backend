package com.example.ecommerce.temporal.workflow;

import com.example.ecommerce.temporal.model.OrderDetails;
import com.example.ecommerce.temporal.model.OrderProgress;
import com.example.ecommerce.temporal.model.OrderResult;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Completed-order flow: process payment, check/reserve inventory, ship, notify the customer.
 * If any step fails permanently, completed steps are compensated (stock released, payment refunded)
 * and the customer is told the order failed.
 */
@WorkflowInterface
public interface OrderWorkflow {

    @WorkflowMethod
    OrderResult processOrder(OrderDetails order);

    @QueryMethod
    OrderProgress getProgress();
}
