package com.portfolio.javaqualityservicelab.approval.api;

import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ApprovalControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void cleanDatabase() {
        approvalRequestRepository.deleteAll();
    }

    @Test
    void createGetAndListFlowWorks() throws Exception {
        String createPayload = """
                {
                  "subject": "Travel Budget",
                  "description": "Attend Java conference",
                  "requestedBy": "alice",
                  "approver": "manager"
                }
                """;

        String location = mockMvc.perform(post("/api/v1/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        String locationPath = URI.create(location).getPath();

        mockMvc.perform(get(locationPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Travel Budget"))
                .andExpect(jsonPath("$.requestedBy").value("alice"))
                .andExpect(jsonPath("$.approver").value("manager"));

        mockMvc.perform(get("/api/v1/approvals?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void approvalTransitionAndConflictBehaviorWorks() throws Exception {
        String createPayload = """
                {
                  "subject": "Hardware Refresh",
                  "description": "New dev machine",
                  "requestedBy": "alice",
                  "approver": "manager"
                }
                """;

        String location = mockMvc.perform(post("/api/v1/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        String locationPath = URI.create(location).getPath();

        String approvePayload = """
                {
                  "actor": "manager"
                }
                """;

        mockMvc.perform(post(locationPath + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String rejectPayload = """
                {
                  "actor": "manager",
                  "reason": "Missing details"
                }
                """;

        mockMvc.perform(post(locationPath + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("request is already in terminal state: APPROVED"));
    }

    @Test
    void validationAndNotFoundErrorsAreReturned() throws Exception {
        String invalidCreatePayload = """
                {
                  "subject": "",
                  "description": "Desc",
                  "requestedBy": "alice",
                  "approver": "manager"
                }
                """;

        mockMvc.perform(post("/api/v1/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidCreatePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.subject").exists());

        String approvePayload = """
                {
                  "actor": "manager"
                }
                """;

        mockMvc.perform(post("/api/v1/approvals/%s/approve".formatted(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvePayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createWithSameRequesterAndApproverReturnsConflict() throws Exception {
        String invalidBusinessPayload = """
                {
                  "subject": "Tooling",
                  "description": "License purchase",
                  "requestedBy": "alice",
                  "approver": "alice"
                }
                """;

        mockMvc.perform(post("/api/v1/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBusinessPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("requester and approver must be different users"));
    }
}
