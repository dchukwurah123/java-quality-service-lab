package com.portfolio.javaqualityservicelab.approval.application;

public record ReturnApprovalCommand(
        String actor,
        String comment
) {
}
