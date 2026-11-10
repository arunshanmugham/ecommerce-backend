package com.example.ecommerce.service;

/**
 * A permanent business failure (declined card, out of stock, undeliverable address...).
 * Activities convert it into a non-retryable Temporal ApplicationFailure, so the workflow
 * stops retrying and runs its compensations instead.
 */
public class BusinessException extends RuntimeException {

    private final String type;

    public BusinessException(String type, String message) {
        super(message);
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
