package com.example.ecommerce.web;

import com.example.ecommerce.config.TemporalProperties;
import com.example.ecommerce.domain.CustomerOrder;
import com.example.ecommerce.repository.InventoryReservationRepository;
import com.example.ecommerce.repository.NotificationRepository;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.repository.ShipmentRepository;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.temporal.model.OrderProgress;
import com.example.ecommerce.temporal.workflow.OrderWorkflow;
import com.example.ecommerce.web.dto.CreateOrderRequest;
import com.example.ecommerce.web.dto.OrderAuditResponse;
import com.example.ecommerce.web.dto.OrderResponse;
import com.example.ecommerce.web.dto.WorkflowStatusResponse;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final WorkflowClient workflowClient;
    private final WorkflowServiceStubs serviceStubs;
    private final TemporalProperties temporalProps;
    private final PaymentRepository payments;
    private final InventoryReservationRepository reservations;
    private final ShipmentRepository shipments;
    private final NotificationRepository notifications;

    public OrderController(OrderService orderService, WorkflowClient workflowClient,
                           WorkflowServiceStubs serviceStubs, TemporalProperties temporalProps,
                           PaymentRepository payments, InventoryReservationRepository reservations,
                           ShipmentRepository shipments, NotificationRepository notifications) {
        this.orderService = orderService;
        this.workflowClient = workflowClient;
        this.serviceStubs = serviceStubs;
        this.temporalProps = temporalProps;
        this.payments = payments;
        this.reservations = reservations;
        this.shipments = shipments;
        this.notifications = notifications;
    }

    /** Places an order and starts its Temporal workflow. Returns immediately (202) with status PENDING. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        return OrderResponse.from(orderService.placeOrder(request));
    }

    @GetMapping
    public List<OrderResponse> list() {
        return orderService.list().stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse get(@PathVariable String orderNumber) {
        return OrderResponse.from(orderService.get(orderNumber));
    }

    /** Live view from Temporal: execution status plus the workflow's getProgress() query. */
    @GetMapping("/{orderNumber}/workflow")
    public WorkflowStatusResponse workflow(@PathVariable String orderNumber) {
        CustomerOrder order = orderService.get(orderNumber);
        String workflowId = order.getWorkflowId();

        String status = serviceStubs.blockingStub().describeWorkflowExecution(
                        DescribeWorkflowExecutionRequest.newBuilder()
                                .setNamespace(temporalProps.namespace())
                                .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId))
                                .build())
                .getWorkflowExecutionInfo().getStatus().name()
                .replace("WORKFLOW_EXECUTION_STATUS_", "");

        OrderProgress progress = null;
        try {
            progress = workflowClient.newWorkflowStub(OrderWorkflow.class, workflowId).getProgress();
        } catch (RuntimeException e) {
            log.warn("Query on workflow {} failed: {}", workflowId, e.getMessage());
        }
        return new WorkflowStatusResponse(workflowId, status, progress);
    }

    /** What each service recorded in MySQL for this order. */
    @GetMapping("/{orderNumber}/audit")
    public OrderAuditResponse audit(@PathVariable String orderNumber) {
        CustomerOrder order = orderService.get(orderNumber);
        return new OrderAuditResponse(
                OrderResponse.from(order),
                payments.findByOrderNumber(orderNumber).orElse(null),
                reservations.findByOrderNumber(orderNumber),
                shipments.findByOrderNumber(orderNumber).orElse(null),
                notifications.findByOrderNumberOrderBySentAtAsc(orderNumber));
    }
}
