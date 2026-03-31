package com.portfolio.javaqualityservicelab.approval.api;

import com.portfolio.javaqualityservicelab.approval.application.ApprovalService;
import com.portfolio.javaqualityservicelab.approval.domain.ApprovalStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/requests")
public class ApprovalRequestController {

    private final ApprovalService approvalService;

    public ApprovalRequestController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping
    public ResponseEntity<ApprovalResponse> createApproval(@Valid @RequestBody CreateApprovalRequest request) {
        ApprovalResponse response = ApprovalResponse.from(
                approvalService.createApproval(
                        request.subject(),
                        request.description(),
                        request.requestedBy(),
                        request.approver()
                )
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public ApprovalResponse updateApproval(@PathVariable UUID id, @Valid @RequestBody UpdateApprovalRequest request) {
        return ApprovalResponse.from(
                approvalService.updateApproval(
                        id,
                        request.subject(),
                        request.description(),
                        request.approver()
                )
        );
    }

    @GetMapping("/{id}")
    public ApprovalResponse getApproval(@PathVariable UUID id) {
        return ApprovalResponse.from(approvalService.getApproval(id));
    }

    @GetMapping
    public List<ApprovalResponse> listRequests(
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(required = false) String requestedBy,
            @RequestParam(required = false) String approver
    ) {
        return approvalService.listRequests(status, requestedBy, approver)
                .stream()
                .map(ApprovalResponse::from)
                .toList();
    }

    @PostMapping("/{id}/submit")
    public ApprovalResponse submitApproval(@PathVariable UUID id, @Valid @RequestBody SubmitApprovalRequest request) {
        return ApprovalResponse.from(
                approvalService.submitApproval(id, request.actor())
        );
    }

    @PostMapping("/{id}/approve")
    public ApprovalResponse approveApproval(@PathVariable UUID id, @Valid @RequestBody ApproveApprovalRequest request) {
        return ApprovalResponse.from(
                approvalService.approveApproval(id, request.actor())
        );
    }

    @PostMapping("/{id}/return")
    public ApprovalResponse returnApproval(@PathVariable UUID id, @Valid @RequestBody ReturnApprovalRequest request) {
        return ApprovalResponse.from(
                approvalService.returnApproval(id, request.actor(), request.comment())
        );
    }

    @GetMapping("/{id}/audit")
    public List<ApprovalAuditResponse> listAuditHistory(@PathVariable UUID id) {
        return approvalService.listAuditHistory(id)
                .stream()
                .map(ApprovalAuditResponse::from)
                .toList();
    }
}
