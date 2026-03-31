package com.portfolio.javaqualityservicelab.approval.application;

import com.portfolio.javaqualityservicelab.approval.domain.ApprovalRequest;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalAuditEntry;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalStatus;
import com.portfolio.javaqualityservicelab.approval.domain.AuditAction;
import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalAuditEntryRepository;
import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalRequestRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ApprovalService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalAuditEntryRepository approvalAuditEntryRepository;

    public ApprovalService(
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalAuditEntryRepository approvalAuditEntryRepository
    ) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.approvalAuditEntryRepository = approvalAuditEntryRepository;
    }

    @Transactional
    public ApprovalRequest createApproval(CreateApprovalCommand command) {
        String subject = requireText(command.subject(), "subject is required");
        String description = requireText(command.description(), "description is required");
        String requestedBy = requireText(command.requestedBy(), "requestedBy is required");
        String approver = requireText(command.approver(), "approver is required");
        ensureUsersAreDifferent(requestedBy, approver);

        ApprovalRequest approvalRequest = ApprovalRequest.createDraft(subject, description, requestedBy, approver);
        return approvalRequestRepository.save(approvalRequest);
    }

    @Transactional
    public ApprovalRequest updateApproval(UUID id, UpdateApprovalCommand command) {
        ApprovalRequest approvalRequest = getApproval(id);
        ensureEditable(approvalRequest.getStatus());

        String subject = requireText(command.subject(), "subject is required");
        String description = requireText(command.description(), "description is required");
        String approver = requireText(command.approver(), "approver is required");
        ensureUsersAreDifferent(approvalRequest.getRequestedBy(), approver);

        approvalRequest.updateDraftFields(subject, description, approver);
        return approvalRequestRepository.save(approvalRequest);
    }

    @Transactional
    public ApprovalRequest submitApproval(UUID id, SubmitApprovalCommand command) {
        ApprovalRequest approvalRequest = getApproval(id);
        String actor = requireText(command.actor(), "actor is required");
        ensureSubmitAllowed(approvalRequest, actor);

        approvalRequest.markSubmitted();
        return approvalRequestRepository.save(approvalRequest);
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

        if (status == null && requestedByFilter == null && approverFilter == null) {
            return approvalRequestRepository.findAll(DEFAULT_SORT);
        }
        if (status != null && requestedByFilter != null && approverFilter != null) {
            return approvalRequestRepository.findByStatusAndRequestedByIgnoreCaseAndApproverIgnoreCase(
                    status,
                    requestedByFilter,
                    approverFilter,
                    DEFAULT_SORT
            );
        }
        if (status != null && requestedByFilter != null) {
            return approvalRequestRepository.findByStatusAndRequestedByIgnoreCase(status, requestedByFilter, DEFAULT_SORT);
        }
        if (status != null && approverFilter != null) {
            return approvalRequestRepository.findByStatusAndApproverIgnoreCase(status, approverFilter, DEFAULT_SORT);
        }
        if (requestedByFilter != null && approverFilter != null) {
            return approvalRequestRepository.findByRequestedByIgnoreCaseAndApproverIgnoreCase(
                    requestedByFilter,
                    approverFilter,
                    DEFAULT_SORT
            );
        }
        if (status != null) {
            return approvalRequestRepository.findByStatus(status, DEFAULT_SORT);
        }
        if (requestedByFilter != null) {
            return approvalRequestRepository.findByRequestedByIgnoreCase(requestedByFilter, DEFAULT_SORT);
        }
        return approvalRequestRepository.findByApproverIgnoreCase(approverFilter, DEFAULT_SORT);
    }

    @Transactional
    public ApprovalRequest approveApproval(UUID id, ApproveApprovalCommand command) {
        ApprovalRequest approvalRequest = getApproval(id);
        String actor = requireText(command.actor(), "actor is required");
        ensureSubmitted(approvalRequest.getStatus(), "only submitted requests can be approved");
        ensureApprover(approvalRequest, actor, "only the assigned approver can approve this request");

        approvalRequest.markApproved();
        ApprovalRequest saved = approvalRequestRepository.save(approvalRequest);
        approvalAuditEntryRepository.save(ApprovalAuditEntry.of(saved, AuditAction.APPROVED, actor, null));
        return saved;
    }

    @Transactional
    public ApprovalRequest returnApproval(UUID id, ReturnApprovalCommand command) {
        ApprovalRequest approvalRequest = getApproval(id);
        String actor = requireText(command.actor(), "actor is required");
        String comment = requireText(command.comment(), "comment is required when returning a request");
        ensureSubmitted(approvalRequest.getStatus(), "only submitted requests can be returned");
        ensureApprover(approvalRequest, actor, "only the assigned approver can return this request");

        approvalRequest.markReturned(comment);
        ApprovalRequest saved = approvalRequestRepository.save(approvalRequest);
        approvalAuditEntryRepository.save(ApprovalAuditEntry.of(saved, AuditAction.RETURNED, actor, comment));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ApprovalAuditEntry> listAuditHistory(UUID id) {
        ApprovalRequest approvalRequest = getApproval(id);
        return approvalAuditEntryRepository.findByApprovalRequestOrderByOccurredAtAsc(approvalRequest);
    }

    private String normalizeFilter(String value) {
        if (Objects.isNull(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String requireText(String value, String message) {
        String normalized = normalizeFilter(value);
        if (normalized == null) {
            throw new ApprovalValidationException(message);
        }
        return normalized;
    }

    private void ensureUsersAreDifferent(String requestedBy, String approver) {
        if (requestedBy.equalsIgnoreCase(approver)) {
            throw new ApprovalValidationException("requester and approver must be different users");
        }
    }

    private void ensureEditable(ApprovalStatus status) {
        if (status != ApprovalStatus.DRAFT && status != ApprovalStatus.RETURNED) {
            throw new ApprovalStateTransitionException("only draft or returned requests can be updated");
        }
    }

    private void ensureSubmitAllowed(ApprovalRequest approvalRequest, String actor) {
        ApprovalStatus status = approvalRequest.getStatus();
        if (status != ApprovalStatus.DRAFT && status != ApprovalStatus.RETURNED) {
            throw new ApprovalStateTransitionException("only draft or returned requests can be submitted");
        }
        if (!approvalRequest.getRequestedBy().equalsIgnoreCase(actor)) {
            throw new ApprovalStateTransitionException("only requester can submit this request");
        }
    }

    private void ensureSubmitted(ApprovalStatus status, String message) {
        if (status != ApprovalStatus.SUBMITTED) {
            throw new ApprovalStateTransitionException(message);
        }
    }

    private void ensureApprover(ApprovalRequest approvalRequest, String actor, String message) {
        if (!approvalRequest.getApprover().equalsIgnoreCase(actor)) {
            throw new ApprovalStateTransitionException(message);
        }
    }
}
