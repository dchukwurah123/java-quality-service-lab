package com.portfolio.javaqualityservicelab.approval.application;

import java.util.Objects;

public record SubmitApprovalCommand(String actor) {

    public SubmitApprovalCommand {
        Objects.requireNonNull(actor, "actor is required");
    }
}
