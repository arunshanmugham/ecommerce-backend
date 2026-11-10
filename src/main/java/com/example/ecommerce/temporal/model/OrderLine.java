package com.example.ecommerce.temporal.model;

import java.math.BigDecimal;

/** One line item of an order, as passed to the workflow and activities. */
public class OrderLine {

    private String sku;
    private int quantity;
    private BigDecimal unitPrice;

    public OrderLine() {
    }

    public OrderLine(String sku, int quantity, BigDecimal unitPrice) {
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
