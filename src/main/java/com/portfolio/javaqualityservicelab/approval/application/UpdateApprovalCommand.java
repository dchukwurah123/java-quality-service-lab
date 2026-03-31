package com.portfolio.javaqualityservicelab.approval.application;

public record UpdateApprovalCommand(
        String subject,
        String description,
        String approver
) {
}
