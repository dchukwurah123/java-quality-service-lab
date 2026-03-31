package com.portfolio.javaqualityservicelab.approval.application;

import com.portfolio.javaqualityservicelab.approval.domain.ApprovalRequest;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalAuditEntry;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalStatus;
import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalAuditEntryRepository;
import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private ApprovalAuditEntryRepository approvalAuditEntryRepository;

    @InjectMocks
    private ApprovalService approvalService;

    @Test
    void createApprovalSetsDraftStatus() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequest created = approvalService.createApproval(
                new CreateApprovalCommand(
                        "Purchase laptop",
                        "Developer equipment request",
                        "alice",
                        "manager"
                )
        );

        assertNotNull(created.getId());
        assertEquals(ApprovalStatus.DRAFT, created.getStatus());
        verify(approvalRequestRepository).save(any(ApprovalRequest.class));
    }

    @Test
    void createApprovalWithSameRequesterAndApproverThrowsConflict() {
        ApprovalValidationException exception = assertThrows(
                ApprovalValidationException.class,
                () -> approvalService.createApproval(
                        new CreateApprovalCommand(
                                "Purchase laptop",
                                "Developer equipment request",
                                "alice",
                                "alice"
                        )
                )
        );

        assertEquals("requester and approver must be different users", exception.getMessage());
    }

    @Test
    void submitThenApproveCreatesAuditAndTransitionsToApproved() {
        ApprovalRequest approvalRequest = ApprovalRequest.createDraft(
                "Travel budget",
                "Conference attendance",
                "alice",
                "manager"
        );
        approvalRequest.markSubmitted();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));
        when(approvalRequestRepository.save(approvalRequest)).thenReturn(approvalRequest);
        when(approvalAuditEntryRepository.save(any(ApprovalAuditEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequest result = approvalService.approveApproval(
                approvalRequest.getId(),
                new ApproveApprovalCommand("manager")
        );

        assertEquals(ApprovalStatus.APPROVED, result.getStatus());
        assertNotNull(result.getDecisionAt());
        verify(approvalRequestRepository).save(approvalRequest);
        verify(approvalAuditEntryRepository).save(any(ApprovalAuditEntry.class));
    }

    @Test
    void updateIsAllowedWhenReturned() {
        ApprovalRequest approvalRequest = ApprovalRequest.createDraft(
                "Travel budget",
                "Conference attendance",
                "alice",
                "manager"
        );
        approvalRequest.markSubmitted();
        approvalRequest.markReturned("needs more details");
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));
        when(approvalRequestRepository.save(approvalRequest)).thenReturn(approvalRequest);

        ApprovalRequest updated = approvalService.updateApproval(
                approvalRequest.getId(),
                new UpdateApprovalCommand("Updated subject", "Updated description", "manager")
        );

        assertEquals("Updated subject", updated.getSubject());
        assertEquals(ApprovalStatus.RETURNED, updated.getStatus());
    }

    @Test
    void updateIsBlockedWhenSubmitted() {
        ApprovalRequest approvalRequest = ApprovalRequest.createDraft(
                "Cloud credits",
                "Sandbox budget",
                "alice",
                "manager"
        );
        approvalRequest.markSubmitted();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));

        ApprovalStateTransitionException exception = assertThrows(
                ApprovalStateTransitionException.class,
                () -> approvalService.updateApproval(
                        approvalRequest.getId(),
                        new UpdateApprovalCommand("new subject", "new description", "manager")
                )
        );

        assertEquals("only draft or returned requests can be updated", exception.getMessage());
    }

    @Test
    void returnWithoutCommentThrowsValidation() {
        ApprovalRequest approvalRequest = ApprovalRequest.createDraft(
                "Headcount request",
                "New QA role",
                "alice",
                "manager"
        );
        approvalRequest.markSubmitted();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));

        ApprovalValidationException exception = assertThrows(
                ApprovalValidationException.class,
                () -> approvalService.returnApproval(
                        approvalRequest.getId(),
                        new ReturnApprovalCommand("manager", " ")
                )
        );

        assertEquals("comment is required when returning a request", exception.getMessage());
    }

    @Test
    void submitMissingApprovalThrowsNotFound() {
        UUID missingId = UUID.randomUUID();
        when(approvalRequestRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> approvalService.submitApproval(missingId, new SubmitApprovalCommand("alice"))
        );
    }
}
