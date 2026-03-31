package com.portfolio.javaqualityservicelab.approval.application;

import com.portfolio.javaqualityservicelab.approval.domain.ApprovalAuditEntry;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalRequest;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalStatus;
import com.portfolio.javaqualityservicelab.approval.domain.AuditAction;
import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalAuditEntryRepository;
import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
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
    void should_createDraft_when_createRequestIsValid() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequest created = approvalService.createApproval(
                "Purchase laptop",
                "Developer equipment request",
                "alice",
                "manager"
        );

        assertNotNull(created.getId());
        assertEquals(ApprovalStatus.DRAFT, created.getStatus());
        verify(approvalRequestRepository).save(any(ApprovalRequest.class));
    }

    @Test
    void should_updateDraft_when_requestIsDraft() {
        ApprovalRequest approvalRequest = createDraftRequest();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));
        when(approvalRequestRepository.save(approvalRequest)).thenReturn(approvalRequest);

        ApprovalRequest updated = approvalService.updateApproval(
                approvalRequest.getId(),
                "Updated subject",
                "Updated description",
                "manager"
        );

        assertEquals("Updated subject", updated.getSubject());
        assertEquals("Updated description", updated.getDescription());
        assertEquals(ApprovalStatus.DRAFT, updated.getStatus());
    }

    @Test
    void should_rejectUpdate_when_requestIsApproved() {
        ApprovalRequest approvalRequest = createSubmittedRequest();
        approvalRequest.markApproved();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));

        ApprovalStateTransitionException exception = assertThrows(
                ApprovalStateTransitionException.class,
                () -> approvalService.updateApproval(
                        approvalRequest.getId(),
                        "new subject",
                        "new description",
                        "manager"
                )
        );

        assertTrue(exception.getMessage().contains("draft or returned"));
    }

    @Test
    void should_submitDraft_when_requestIsDraftAndActorIsRequester() {
        ApprovalRequest approvalRequest = createDraftRequest();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));
        when(approvalRequestRepository.save(approvalRequest)).thenReturn(approvalRequest);

        ApprovalRequest submitted = approvalService.submitApproval(
                approvalRequest.getId(),
                "alice"
        );

        assertEquals(ApprovalStatus.SUBMITTED, submitted.getStatus());
    }

    @Test
    void should_rejectSubmit_when_requestIsNotDraftOrReturned() {
        ApprovalRequest approvalRequest = createSubmittedRequest();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));

        ApprovalStateTransitionException exception = assertThrows(
                ApprovalStateTransitionException.class,
                () -> approvalService.submitApproval(
                        approvalRequest.getId(),
                        "alice"
                )
        );

        assertTrue(exception.getMessage().contains("draft or returned"));
    }

    @Test
    void should_approveSubmitted_when_actorIsAssignedApprover() {
        ApprovalRequest approvalRequest = createSubmittedRequest();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));
        when(approvalRequestRepository.save(approvalRequest)).thenReturn(approvalRequest);
        when(approvalAuditEntryRepository.save(any(ApprovalAuditEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequest approved = approvalService.approveApproval(
                approvalRequest.getId(),
                "manager"
        );

        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        assertNotNull(approved.getDecisionAt());
        verify(approvalAuditEntryRepository).save(any(ApprovalAuditEntry.class));
    }

    @Test
    void should_rejectApprove_when_requestIsDraft() {
        ApprovalRequest approvalRequest = createDraftRequest();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));

        ApprovalStateTransitionException exception = assertThrows(
                ApprovalStateTransitionException.class,
                () -> approvalService.approveApproval(
                        approvalRequest.getId(),
                        "manager"
                )
        );

        assertTrue(exception.getMessage().contains("submitted"));
    }

    @Test
    void should_returnSubmitted_when_commentIsProvided() {
        ApprovalRequest approvalRequest = createSubmittedRequest();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));
        when(approvalRequestRepository.save(approvalRequest)).thenReturn(approvalRequest);
        when(approvalAuditEntryRepository.save(any(ApprovalAuditEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequest returned = approvalService.returnApproval(
                approvalRequest.getId(),
                "manager",
                "Please add a vendor quote"
        );

        assertEquals(ApprovalStatus.RETURNED, returned.getStatus());
        assertEquals("Please add a vendor quote", returned.getLatestComment());
        verify(approvalAuditEntryRepository).save(any(ApprovalAuditEntry.class));
    }

    @Test
    void should_rejectReturn_when_commentIsBlank() {
        ApprovalRequest approvalRequest = createSubmittedRequest();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));

        ApprovalValidationException exception = assertThrows(
                ApprovalValidationException.class,
                () -> approvalService.returnApproval(
                        approvalRequest.getId(),
                        "manager",
                        " "
                )
        );

        assertTrue(exception.getMessage().contains("comment is required"));
    }

    @Test
    void should_createAuditEntriesCorrectly_when_returnThenApproveFlowRuns() {
        ApprovalRequest approvalRequest = createSubmittedRequest();
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));
        when(approvalRequestRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalAuditEntryRepository.save(any(ApprovalAuditEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        approvalService.returnApproval(
                approvalRequest.getId(),
                "manager",
                "Need budget details"
        );
        approvalService.submitApproval(
                approvalRequest.getId(),
                "alice"
        );
        approvalService.approveApproval(
                approvalRequest.getId(),
                "manager"
        );

        ArgumentCaptor<ApprovalAuditEntry> captor = ArgumentCaptor.forClass(ApprovalAuditEntry.class);
        verify(approvalAuditEntryRepository, times(2)).save(captor.capture());

        List<ApprovalAuditEntry> savedEntries = captor.getAllValues();
        assertEquals(AuditAction.RETURNED, savedEntries.get(0).getAction());
        assertEquals("Need budget details", savedEntries.get(0).getComment());
        assertEquals(AuditAction.APPROVED, savedEntries.get(1).getAction());
        assertNull(savedEntries.get(1).getComment());
    }

    @Test
    void should_throwNotFound_when_submitTargetDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(approvalRequestRepository.findById(missingId)).thenReturn(Optional.empty());

        ApprovalRequestNotFoundException exception = assertThrows(
                ApprovalRequestNotFoundException.class,
                () -> approvalService.submitApproval(missingId, "alice")
        );
        assertEquals("approval request not found: " + missingId, exception.getMessage());
    }

    private ApprovalRequest createDraftRequest() {
        return ApprovalRequest.createDraft(
                "Travel budget",
                "Conference attendance",
                "alice",
                "manager"
        );
    }

    private ApprovalRequest createSubmittedRequest() {
        ApprovalRequest approvalRequest = createDraftRequest();
        approvalRequest.markSubmitted();
        return approvalRequest;
    }
}
