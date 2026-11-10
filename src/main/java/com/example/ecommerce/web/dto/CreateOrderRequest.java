package com.example.ecommerce.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerName,
        @NotBlank @Email String customerEmail,
        @NotBlank String shippingAddress,
        /* Payment provider token. Test values: tok_visa, tok_flaky, tok_declined, tok_insufficient_funds */
        @NotBlank String paymentToken,
        @NotEmpty List<@Valid Item> items) {

    public record Item(@NotBlank String sku, @Min(1) int quantity) {
    }
}
