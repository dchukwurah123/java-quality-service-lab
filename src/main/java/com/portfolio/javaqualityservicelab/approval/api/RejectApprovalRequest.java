package com.portfolio.javaqualityservicelab.approval.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectApprovalRequest(
        @NotBlank(message = "actor is required")
        @Size(max = 80, message = "actor must be at most 80 characters")
        String actor,
        @NotBlank(message = "reason is required")
        @Size(max = 300, message = "reason must be at most 300 characters")
        String reason
) {
}
