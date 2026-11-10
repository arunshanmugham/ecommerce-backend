package com.example.ecommerce.service;

import com.example.ecommerce.domain.CustomerOrder;
import com.example.ecommerce.domain.OrderItem;
import com.example.ecommerce.domain.OrderStatus;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.temporal.TaskQueues;
import com.example.ecommerce.temporal.model.OrderDetails;
import com.example.ecommerce.temporal.model.OrderLine;
import com.example.ecommerce.temporal.workflow.OrderWorkflow;
import com.example.ecommerce.web.dto.CreateOrderRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final ProductRepository products;
    private final WorkflowClient workflowClient;

    public OrderService(OrderRepository orders, ProductRepository products, WorkflowClient workflowClient) {
        this.orders = orders;
        this.products = products;
        this.workflowClient = workflowClient;
    }

    public static String workflowIdFor(String orderNumber) {
        return "order-" + orderNumber;
    }

    /**
     * Saves the order as PENDING, then starts the OrderWorkflow asynchronously.
     * Prices are taken from the product catalogue, not from the client.
     */
    public CustomerOrder placeOrder(CreateOrderRequest request) {
        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());
        order.setShippingAddress(request.shippingAddress());
        order.setWorkflowId(workflowIdFor(order.getOrderNumber()));

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.Item item : request.items()) {
            Product product = products.findBySku(item.sku())
                    .orElseThrow(() -> new NotFoundException("Unknown product SKU: " + item.sku()));
            order.addItem(new OrderItem(product.getSku(), item.quantity(), product.getPrice()));
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        order.setTotalAmount(total);
        CustomerOrder saved = orders.save(order); // committed before the workflow can touch it

        OrderDetails details = toWorkflowInput(saved, request.paymentToken());
        OrderWorkflow workflow = workflowClient.newWorkflowStub(OrderWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(saved.getWorkflowId())
                        .setTaskQueue(TaskQueues.ORDER)
                        .build());
        try {
            WorkflowClient.start(workflow::processOrder, details);
        } catch (RuntimeException e) {
            saved.setStatus(OrderStatus.FAILED);
            saved.setFailureReason("Could not start order workflow: " + e.getMessage());
            orders.save(saved);
            throw new WorkflowUnavailableException(
                    "Order " + saved.getOrderNumber() + " saved but the workflow could not be started. "
                            + "Is the Temporal server running?", e);
        }
        log.info("Order {} placed, workflow {} started", saved.getOrderNumber(), saved.getWorkflowId());
        return saved;
    }

    @Transactional(readOnly = true)
    public CustomerOrder get(String orderNumber) {
        return orders.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderNumber));
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> list() {
        return orders.findAllByOrderByCreatedAtDesc();
    }

    /** Called by OrderActivities as the workflow progresses. Null arguments are ignored. */
    @Transactional
    public void updateOrder(String orderNumber, OrderStatus status, String transactionId,
                            String trackingNumber, String failureReason) {
        CustomerOrder order = get(orderNumber);
        if (status != null) order.setStatus(status);
        if (transactionId != null) order.setPaymentTransactionId(transactionId);
        if (trackingNumber != null) order.setTrackingNumber(trackingNumber);
        if (failureReason != null) order.setFailureReason(failureReason);
    }

    private static OrderDetails toWorkflowInput(CustomerOrder order, String paymentToken) {
        OrderDetails d = new OrderDetails();
        d.setOrderNumber(order.getOrderNumber());
        d.setCustomerName(order.getCustomerName());
        d.setCustomerEmail(order.getCustomerEmail());
        d.setShippingAddress(order.getShippingAddress());
        d.setPaymentToken(paymentToken);
        d.setTotalAmount(order.getTotalAmount());
        d.setItems(order.getItems().stream()
                .map(i -> new OrderLine(i.getSku(), i.getQuantity(), i.getUnitPrice()))
                .toList());
        return d;
    }
}
