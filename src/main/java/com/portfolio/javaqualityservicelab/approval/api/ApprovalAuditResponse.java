package com.portfolio.javaqualityservicelab.approval.api;

import com.portfolio.javaqualityservicelab.approval.domain.ApprovalAuditEntry;
import com.portfolio.javaqualityservicelab.approval.domain.AuditAction;

import java.time.Instant;

public record ApprovalAuditResponse(
        Long id,
        AuditAction action,
        String actor,
        String comment,
        Instant occurredAt
) {
    public static ApprovalAuditResponse from(ApprovalAuditEntry entry) {
        return new ApprovalAuditResponse(
                entry.getId(),
                entry.getAction(),
                entry.getActor(),
                entry.getComment(),
                entry.getOccurredAt()
        );
    }
}
