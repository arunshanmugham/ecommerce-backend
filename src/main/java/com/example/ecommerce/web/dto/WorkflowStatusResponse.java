package com.example.ecommerce.web.dto;

import com.example.ecommerce.temporal.model.OrderProgress;

/**
 * @param executionStatus Temporal execution status, e.g. RUNNING, COMPLETED, FAILED
 * @param progress        live workflow state from the getProgress() query (null if the query failed)
 */
public record WorkflowStatusResponse(String workflowId, String executionStatus, OrderProgress progress) {
}
