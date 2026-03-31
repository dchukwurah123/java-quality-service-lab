package com.portfolio.javaqualityservicelab.approval.api;

import com.portfolio.javaqualityservicelab.approval.domain.ApprovalRequest;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalStatus;

import java.time.Instant;
import java.util.UUID;

public record ApprovalResponse(
        UUID id,
        String subject,
        String description,
        String requestedBy,
        String approver,
        ApprovalStatus status,
        String latestComment,
        Instant createdAt,
        Instant updatedAt,
        Instant decisionAt
) {
    public static ApprovalResponse from(ApprovalRequest approvalRequest) {
        return new ApprovalResponse(
                approvalRequest.getId(),
                approvalRequest.getSubject(),
                approvalRequest.getDescription(),
                approvalRequest.getRequestedBy(),
                approvalRequest.getApprover(),
                approvalRequest.getStatus(),
                approvalRequest.getLatestComment(),
                approvalRequest.getCreatedAt(),
                approvalRequest.getUpdatedAt(),
                approvalRequest.getDecisionAt()
        );
    }
}
