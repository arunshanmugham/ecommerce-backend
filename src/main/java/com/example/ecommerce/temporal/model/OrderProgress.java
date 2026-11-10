package com.example.ecommerce.temporal.model;

import com.example.ecommerce.domain.OrderStatus;

import java.util.ArrayList;
import java.util.List;

/** Live workflow state, exposed through the getProgress() query. */
public class OrderProgress {

    private OrderStatus status = OrderStatus.PENDING;
    private String currentStep = "STARTED";
    private List<String> history = new ArrayList<>();

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public List<String> getHistory() { return history; }
    public void setHistory(List<String> history) { this.history = history; }
}
