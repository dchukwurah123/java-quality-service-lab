package com.portfolio.javaqualityservicelab.approval.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApprovalRequest(
        @NotBlank(message = "subject is required")
        @Size(max = 120, message = "subject must be at most 120 characters")
        String subject,
        @NotBlank(message = "description is required")
        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,
        @NotBlank(message = "requestedBy is required")
        @Size(max = 80, message = "requestedBy must be at most 80 characters")
        String requestedBy,
        @NotBlank(message = "approver is required")
        @Size(max = 80, message = "approver must be at most 80 characters")
        String approver
) {
}
