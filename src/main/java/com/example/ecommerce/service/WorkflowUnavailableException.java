package com.example.ecommerce.service;

public class WorkflowUnavailableException extends RuntimeException {

    public WorkflowUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
