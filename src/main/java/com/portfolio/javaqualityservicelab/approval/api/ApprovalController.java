package com.portfolio.javaqualityservicelab.approval.api;

import com.portfolio.javaqualityservicelab.approval.application.ApprovalService;
import com.portfolio.javaqualityservicelab.approval.application.ApproveApprovalCommand;
import com.portfolio.javaqualityservicelab.approval.application.CancelApprovalCommand;
import com.portfolio.javaqualityservicelab.approval.application.CreateApprovalCommand;
import com.portfolio.javaqualityservicelab.approval.application.RejectApprovalCommand;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping
    public ResponseEntity<ApprovalResponse> createApproval(@Valid @RequestBody CreateApprovalRequest request) {
        ApprovalResponse response = ApprovalResponse.from(
                approvalService.createApproval(
                        new CreateApprovalCommand(
                                request.subject(),
                                request.description(),
                                request.requestedBy(),
                                request.approver()
                        )
                )
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ApprovalResponse getApproval(@PathVariable UUID id) {
        return ApprovalResponse.from(approvalService.getApproval(id));
    }

    @GetMapping
    public List<ApprovalResponse> listApprovals(
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(required = false) String requestedBy,
            @RequestParam(required = false) String approver
    ) {
        return approvalService.listApprovals(status, requestedBy, approver)
                .stream()
                .map(ApprovalResponse::from)
                .toList();
    }

    @PostMapping("/{id}/approve")
    public ApprovalResponse approveApproval(@PathVariable UUID id, @Valid @RequestBody ApproveApprovalRequest request) {
        return ApprovalResponse.from(
                approvalService.approveApproval(id, new ApproveApprovalCommand(request.actor()))
        );
    }

    @PostMapping("/{id}/reject")
    public ApprovalResponse rejectApproval(@PathVariable UUID id, @Valid @RequestBody RejectApprovalRequest request) {
        return ApprovalResponse.from(
                approvalService.rejectApproval(id, new RejectApprovalCommand(request.actor(), request.reason()))
        );
    }

    @PostMapping("/{id}/cancel")
    public ApprovalResponse cancelApproval(@PathVariable UUID id, @Valid @RequestBody CancelApprovalRequest request) {
        return ApprovalResponse.from(
                approvalService.cancelApproval(id, new CancelApprovalCommand(request.actor(), request.reason()))
        );
    }
}
