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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        String locationPath = URI.create(location).getPath();

        mockMvc.perform(get(locationPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Travel Budget"))
                .andExpect(jsonPath("$.requestedBy").value("alice"))
                .andExpect(jsonPath("$.approver").value("manager"));

        mockMvc.perform(get("/api/v1/approvals?status=DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void updateSubmitReturnResubmitAndApproveFlowWorksWithAudits() throws Exception {
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

        String updatePayload = """
                {
                  "subject": "Hardware Refresh v2",
                  "description": "New machine for performance testing",
                  "approver": "manager"
                }
                """;

        mockMvc.perform(put(locationPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.subject").value("Hardware Refresh v2"));

        String submitPayload = """
                {
                  "actor": "alice"
                }
                """;

        mockMvc.perform(post(locationPath + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        String returnPayload = """
                {
                  "actor": "manager",
                  "comment": "Please include cost breakdown"
                }
                """;

        mockMvc.perform(post(locationPath + "/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.latestComment").value("Please include cost breakdown"));

        mockMvc.perform(post(locationPath + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

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

        mockMvc.perform(get(locationPath + "/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("RETURNED"))
                .andExpect(jsonPath("$[0].comment").value("Please include cost breakdown"))
                .andExpect(jsonPath("$[1].action").value("APPROVED"));
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
    void createWithSameRequesterAndApproverReturnsBadRequest() throws Exception {
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("requester and approver must be different users"));
    }

    @Test
    void returnRequiresComment() throws Exception {
        String createPayload = """
                {
                  "subject": "Procurement",
                  "description": "Order test kits",
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

        mockMvc.perform(post(locationPath + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"alice\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(locationPath + "/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"manager\",\"comment\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.comment").exists());
    }
}
