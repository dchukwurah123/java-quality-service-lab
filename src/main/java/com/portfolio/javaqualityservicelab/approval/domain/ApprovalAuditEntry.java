package com.portfolio.javaqualityservicelab.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "approval_audit_entries")
public class ApprovalAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_request_id", nullable = false)
    private ApprovalRequest approvalRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    @Column(nullable = false, length = 80)
    private String actor;

    @Column(length = 300)
    private String comment;

    @Column(nullable = false)
    private Instant occurredAt;

    protected ApprovalAuditEntry() {
        // Required by JPA
    }

    private ApprovalAuditEntry(
            ApprovalRequest approvalRequest,
            AuditAction action,
            String actor,
            String comment,
            Instant occurredAt
    ) {
        this.approvalRequest = approvalRequest;
        this.action = action;
        this.actor = actor;
        this.comment = comment;
        this.occurredAt = occurredAt;
    }

    public static ApprovalAuditEntry of(ApprovalRequest approvalRequest, AuditAction action, String actor, String comment) {
        return new ApprovalAuditEntry(
                approvalRequest,
                action,
                actor,
                comment,
                Instant.now()
        );
    }

    public Long getId() {
        return id;
    }

    public ApprovalRequest getApprovalRequest() {
        return approvalRequest;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public String getComment() {
        return comment;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
