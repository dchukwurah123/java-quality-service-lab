package com.portfolio.javaqualityservicelab.approval.application;

public record CreateApprovalCommand(
        String subject,
        String description,
        String requestedBy,
        String approver
) {
}
