package com.example.ecommerce.temporal.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Workflow input: everything needed to fulfil a placed order. */
public class OrderDetails {

    private String orderNumber;
    private String customerName;
    private String customerEmail;
    private String shippingAddress;
    /** Opaque token from a payment provider (never raw card data). */
    private String paymentToken;
    private BigDecimal totalAmount;
    private List<OrderLine> items = new ArrayList<>();

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    public String getPaymentToken() { return paymentToken; }
    public void setPaymentToken(String paymentToken) { this.paymentToken = paymentToken; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public List<OrderLine> getItems() { return items; }
    public void setItems(List<OrderLine> items) { this.items = items; }
}
