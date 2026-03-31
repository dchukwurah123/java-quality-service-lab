package com.portfolio.javaqualityservicelab.approval.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalModuleController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "module", "approval",
                "status", "ok"
        );
    }
}
