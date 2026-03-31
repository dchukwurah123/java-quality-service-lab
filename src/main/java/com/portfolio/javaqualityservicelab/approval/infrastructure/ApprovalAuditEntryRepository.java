package com.portfolio.javaqualityservicelab.approval.infrastructure;

import com.portfolio.javaqualityservicelab.approval.domain.ApprovalAuditEntry;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalAuditEntryRepository extends JpaRepository<ApprovalAuditEntry, Long> {

    List<ApprovalAuditEntry> findByApprovalRequestOrderByOccurredAtAsc(ApprovalRequest approvalRequest);
}
