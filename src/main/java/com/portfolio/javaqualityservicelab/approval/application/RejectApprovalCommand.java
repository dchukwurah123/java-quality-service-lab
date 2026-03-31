package com.portfolio.javaqualityservicelab.approval.application;

public record RejectApprovalCommand(
        String actor,
        String reason
) {
}
