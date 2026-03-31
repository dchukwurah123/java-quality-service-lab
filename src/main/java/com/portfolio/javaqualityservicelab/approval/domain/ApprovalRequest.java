package com.portfolio.javaqualityservicelab.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    @Id
    private UUID id;

    @Version
    private long version;

    @Column(nullable = false, length = 120)
    private String subject;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 80)
    private String requestedBy;

    @Column(nullable = false, length = 80)
    private String approver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status;

    @Column(length = 300)
    private String latestComment;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant decisionAt;

    protected ApprovalRequest() {
        // Required by JPA
    }

    private ApprovalRequest(
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
        this.id = id;
        this.subject = subject;
        this.description = description;
        this.requestedBy = requestedBy;
        this.approver = approver;
        this.status = status;
        this.latestComment = latestComment;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.decisionAt = decisionAt;
    }

    public static ApprovalRequest createDraft(String subject, String description, String requestedBy, String approver) {
        Instant now = Instant.now();
        return new ApprovalRequest(
                UUID.randomUUID(),
                subject,
                description,
                requestedBy,
                approver,
                ApprovalStatus.DRAFT,
                null,
                now,
                now,
                null
        );
    }

    public void updateDraftFields(String subject, String description, String approver) {
        this.subject = subject;
        this.description = description;
        this.approver = approver;
        this.updatedAt = Instant.now();
    }

    public void markSubmitted() {
        this.status = ApprovalStatus.SUBMITTED;
        this.updatedAt = Instant.now();
    }

    public void markApproved() {
        Instant now = Instant.now();
        this.status = ApprovalStatus.APPROVED;
        this.latestComment = null;
        decisionAt = now;
        updatedAt = now;
    }

    public void markReturned(String comment) {
        Instant now = Instant.now();
        this.status = ApprovalStatus.RETURNED;
        this.latestComment = comment;
        this.decisionAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getApprover() {
        return approver;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public String getLatestComment() {
        return latestComment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDecisionAt() {
        return decisionAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApprovalRequest other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
