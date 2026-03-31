package com.portfolio.javaqualityservicelab.approval.infrastructure;

import com.portfolio.javaqualityservicelab.approval.domain.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;

public interface ApprovalRequestRepository
        extends JpaRepository<ApprovalRequest, UUID>, JpaSpecificationExecutor<ApprovalRequest> {
}
