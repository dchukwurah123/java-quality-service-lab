package com.portfolio.javaqualityservicelab.approval.application;

import com.portfolio.javaqualityservicelab.approval.domain.ApprovalRequest;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalRuleViolationException;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalStatus;
import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalRequestRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;

    public ApprovalService(ApprovalRequestRepository approvalRequestRepository) {
        this.approvalRequestRepository = approvalRequestRepository;
    }

    @Transactional
    public ApprovalRequest createApproval(CreateApprovalCommand command) {
        try {
            ApprovalRequest approvalRequest = ApprovalRequest.createNew(
                    command.subject(),
                    command.description(),
                    command.requestedBy(),
                    command.approver()
            );
            return approvalRequestRepository.save(approvalRequest);
        } catch (ApprovalRuleViolationException exception) {
            throw new InvalidApprovalActionException(exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ApprovalRequest getApproval(UUID id) {
        return approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("approval request not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> listApprovals(ApprovalStatus status, String requestedBy, String approver) {
        String requestedByFilter = normalizeFilter(requestedBy);
        String approverFilter = normalizeFilter(approver);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        if (status == null && requestedByFilter == null && approverFilter == null) {
            return approvalRequestRepository.findAll(sort);
        }
        if (status != null && requestedByFilter != null && approverFilter != null) {
            return approvalRequestRepository.findByStatusAndRequestedByIgnoreCaseAndApproverIgnoreCase(
                    status,
                    requestedByFilter,
                    approverFilter,
                    sort
            );
        }
        if (status != null && requestedByFilter != null) {
            return approvalRequestRepository.findByStatusAndRequestedByIgnoreCase(status, requestedByFilter, sort);
        }
        if (status != null && approverFilter != null) {
            return approvalRequestRepository.findByStatusAndApproverIgnoreCase(status, approverFilter, sort);
        }
        if (requestedByFilter != null && approverFilter != null) {
            return approvalRequestRepository.findByRequestedByIgnoreCaseAndApproverIgnoreCase(
                    requestedByFilter,
                    approverFilter,
                    sort
            );
        }
        if (status != null) {
            return approvalRequestRepository.findByStatus(status, sort);
        }
        if (requestedByFilter != null) {
            return approvalRequestRepository.findByRequestedByIgnoreCase(requestedByFilter, sort);
        }
        return approvalRequestRepository.findByApproverIgnoreCase(approverFilter, sort);
    }

    @Transactional
    public ApprovalRequest approveApproval(UUID id, ApproveApprovalCommand command) {
        ApprovalRequest approvalRequest = getApproval(id);
        try {
            approvalRequest.approve(command.actor());
            return approvalRequestRepository.save(approvalRequest);
        } catch (ApprovalRuleViolationException exception) {
            throw new InvalidApprovalActionException(exception.getMessage());
        }
    }

    @Transactional
    public ApprovalRequest rejectApproval(UUID id, RejectApprovalCommand command) {
        ApprovalRequest approvalRequest = getApproval(id);
        try {
            approvalRequest.reject(command.actor(), command.reason());
            return approvalRequestRepository.save(approvalRequest);
        } catch (ApprovalRuleViolationException exception) {
            throw new InvalidApprovalActionException(exception.getMessage());
        }
    }

    @Transactional
    public ApprovalRequest cancelApproval(UUID id, CancelApprovalCommand command) {
        ApprovalRequest approvalRequest = getApproval(id);
        try {
            approvalRequest.cancel(command.actor(), command.reason());
            return approvalRequestRepository.save(approvalRequest);
        } catch (ApprovalRuleViolationException exception) {
            throw new InvalidApprovalActionException(exception.getMessage());
        }
    }

    private String normalizeFilter(String value) {
        if (Objects.isNull(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
