package com.portfolio.javaqualityservicelab.approval.application;

import com.portfolio.javaqualityservicelab.approval.domain.ApprovalRequest;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalAuditEntry;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalStatus;
import com.portfolio.javaqualityservicelab.approval.domain.AuditAction;
import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalAuditEntryRepository;
import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalRequestRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
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
    public ApprovalRequest createApproval(String subject, String description, String requestedBy, String approver) {
        String validatedSubject = requireText(subject, "subject is required");
        String validatedDescription = requireText(description, "description is required");
        String validatedRequestedBy = requireText(requestedBy, "requestedBy is required");
        String validatedApprover = requireText(approver, "approver is required");
        ensureUsersAreDifferent(validatedRequestedBy, validatedApprover);

        ApprovalRequest approvalRequest = ApprovalRequest.createDraft(
                validatedSubject,
                validatedDescription,
                validatedRequestedBy,
                validatedApprover
        );
        return approvalRequestRepository.save(approvalRequest);
    }

    @Transactional
    public ApprovalRequest updateApproval(UUID id, String subject, String description, String approver) {
        ApprovalRequest approvalRequest = getApproval(id);
        ensureEditable(approvalRequest.getStatus());

        String validatedSubject = requireText(subject, "subject is required");
        String validatedDescription = requireText(description, "description is required");
        String validatedApprover = requireText(approver, "approver is required");
        ensureUsersAreDifferent(approvalRequest.getRequestedBy(), validatedApprover);

        approvalRequest.updateDraftFields(validatedSubject, validatedDescription, validatedApprover);
        return approvalRequestRepository.save(approvalRequest);
    }

    @Transactional
    public ApprovalRequest submitApproval(UUID id, String actor) {
        ApprovalRequest approvalRequest = getApproval(id);
        String validatedActor = requireText(actor, "actor is required");
        ensureSubmitAllowed(approvalRequest, validatedActor);

        approvalRequest.markSubmitted();
        return approvalRequestRepository.save(approvalRequest);
    }

    @Transactional(readOnly = true)
    public ApprovalRequest getApproval(UUID id) {
        return approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ApprovalRequestNotFoundException("approval request not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> listRequests(ApprovalStatus status, String requestedBy, String approver) {
        String requestedByFilter = normalizeFilter(requestedBy);
        String approverFilter = normalizeFilter(approver);
        Specification<ApprovalRequest> spec = Specification.where(null);

        if (status != null) {
            spec = spec.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status));
        }
        if (requestedByFilter != null) {
            String requestedByLower = requestedByFilter.toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            criteriaBuilder.lower(root.get("requestedBy")),
                            requestedByLower
                    )
            );
        }
        if (approverFilter != null) {
            String approverLower = approverFilter.toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            criteriaBuilder.lower(root.get("approver")),
                            approverLower
                    )
            );
        }

        return approvalRequestRepository.findAll(spec, DEFAULT_SORT);
    }

    @Transactional
    public ApprovalRequest approveApproval(UUID id, String actor) {
        ApprovalRequest approvalRequest = getApproval(id);
        String validatedActor = requireText(actor, "actor is required");
        ensureSubmitted(approvalRequest.getStatus(), "only submitted requests can be approved");
        ensureApprover(approvalRequest, validatedActor, "only the assigned approver can approve this request");

        approvalRequest.markApproved();
        ApprovalRequest saved = approvalRequestRepository.save(approvalRequest);
        approvalAuditEntryRepository.save(ApprovalAuditEntry.of(saved, AuditAction.APPROVED, validatedActor, null));
        return saved;
    }

    @Transactional
    public ApprovalRequest returnApproval(UUID id, String actor, String comment) {
        ApprovalRequest approvalRequest = getApproval(id);
        String validatedActor = requireText(actor, "actor is required");
        String validatedComment = requireText(comment, "comment is required when returning a request");
        ensureSubmitted(approvalRequest.getStatus(), "only submitted requests can be returned");
        ensureApprover(approvalRequest, validatedActor, "only the assigned approver can return this request");

        approvalRequest.markReturned(validatedComment);
        ApprovalRequest saved = approvalRequestRepository.save(approvalRequest);
        approvalAuditEntryRepository.save(ApprovalAuditEntry.of(saved, AuditAction.RETURNED, validatedActor, validatedComment));
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
