package com.portfolio.javaqualityservicelab.approval.infrastructure;

import com.portfolio.javaqualityservicelab.approval.domain.ApprovalRequest;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    List<ApprovalRequest> findByStatus(ApprovalStatus status, Sort sort);

    List<ApprovalRequest> findByRequestedByIgnoreCase(String requestedBy, Sort sort);

    List<ApprovalRequest> findByApproverIgnoreCase(String approver, Sort sort);

    List<ApprovalRequest> findByStatusAndRequestedByIgnoreCase(ApprovalStatus status, String requestedBy, Sort sort);

    List<ApprovalRequest> findByStatusAndApproverIgnoreCase(ApprovalStatus status, String approver, Sort sort);

    List<ApprovalRequest> findByRequestedByIgnoreCaseAndApproverIgnoreCase(String requestedBy, String approver, Sort sort);

    List<ApprovalRequest> findByStatusAndRequestedByIgnoreCaseAndApproverIgnoreCase(
            ApprovalStatus status,
            String requestedBy,
            String approver,
            Sort sort
    );
}
