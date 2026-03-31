package com.portfolio.javaqualityservicelab.approval.application;

import com.portfolio.javaqualityservicelab.approval.domain.ApprovalRequest;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalStatus;
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

    @InjectMocks
    private ApprovalService approvalService;

    @Test
    void createApprovalSetsPendingStatus() {
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
        assertEquals(ApprovalStatus.PENDING, created.getStatus());
        verify(approvalRequestRepository).save(any(ApprovalRequest.class));
    }

    @Test
    void createApprovalWithSameRequesterAndApproverThrowsConflict() {
        InvalidApprovalActionException exception = assertThrows(
                InvalidApprovalActionException.class,
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
    void approveByAssignedApproverTransitionsToApproved() {
        ApprovalRequest approvalRequest = ApprovalRequest.createNew(
                "Travel budget",
                "Conference attendance",
                "alice",
                "manager"
        );
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));
        when(approvalRequestRepository.save(approvalRequest)).thenReturn(approvalRequest);

        ApprovalRequest result = approvalService.approveApproval(
                approvalRequest.getId(),
                new ApproveApprovalCommand("manager")
        );

        assertEquals(ApprovalStatus.APPROVED, result.getStatus());
        assertNotNull(result.getDecisionAt());
        verify(approvalRequestRepository).save(approvalRequest);
    }

    @Test
    void approveByNonApproverThrowsConflict() {
        ApprovalRequest approvalRequest = ApprovalRequest.createNew(
                "Cloud credits",
                "Sandbox budget",
                "alice",
                "manager"
        );
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));

        InvalidApprovalActionException exception = assertThrows(
                InvalidApprovalActionException.class,
                () -> approvalService.approveApproval(
                        approvalRequest.getId(),
                        new ApproveApprovalCommand("bob")
                )
        );

        assertEquals("only the assigned approver can approve this request", exception.getMessage());
    }

    @Test
    void rejectWithoutReasonThrowsConflict() {
        ApprovalRequest approvalRequest = ApprovalRequest.createNew(
                "Headcount request",
                "New QA role",
                "alice",
                "manager"
        );
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));

        InvalidApprovalActionException exception = assertThrows(
                InvalidApprovalActionException.class,
                () -> approvalService.rejectApproval(
                        approvalRequest.getId(),
                        new RejectApprovalCommand("manager", " ")
                )
        );

        assertEquals("rejection reason is required", exception.getMessage());
    }

    @Test
    void cancelByNonRequesterThrowsConflict() {
        ApprovalRequest approvalRequest = ApprovalRequest.createNew(
                "Software license",
                "Design tool subscription",
                "alice",
                "manager"
        );
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));

        InvalidApprovalActionException exception = assertThrows(
                InvalidApprovalActionException.class,
                () -> approvalService.cancelApproval(
                        approvalRequest.getId(),
                        new CancelApprovalCommand("manager", "No longer needed")
                )
        );

        assertEquals("only the requester can cancel this request", exception.getMessage());
    }

    @Test
    void approveMissingApprovalThrowsNotFound() {
        UUID missingId = UUID.randomUUID();
        when(approvalRequestRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> approvalService.approveApproval(missingId, new ApproveApprovalCommand("manager"))
        );
    }
}
