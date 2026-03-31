package com.portfolio.javaqualityservicelab.approval.application;

public record CancelApprovalCommand(
        String actor,
        String reason
) {
}
