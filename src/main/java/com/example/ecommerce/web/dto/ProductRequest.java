package com.example.ecommerce.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank String sku,
        @NotBlank String name,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @Min(0) int availableQuantity) {
}
