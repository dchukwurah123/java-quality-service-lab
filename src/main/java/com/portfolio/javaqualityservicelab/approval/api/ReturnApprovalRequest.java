package com.portfolio.javaqualityservicelab.approval.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnApprovalRequest(
        @NotBlank(message = "actor is required")
        @Size(max = 80, message = "actor must be at most 80 characters")
        String actor,
        @NotBlank(message = "comment is required")
        @Size(max = 300, message = "comment must be at most 300 characters")
        String comment
) {
}
