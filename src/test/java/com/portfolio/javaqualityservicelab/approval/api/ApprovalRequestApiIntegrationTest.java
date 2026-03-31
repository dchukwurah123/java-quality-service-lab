package com.portfolio.javaqualityservicelab.approval.api;

import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalRequestRepository;
import com.portfolio.javaqualityservicelab.approval.infrastructure.ApprovalAuditEntryRepository;
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
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ApprovalRequestApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private ApprovalAuditEntryRepository approvalAuditEntryRepository;

    @DynamicPropertySource
    @SuppressWarnings("unused")
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    @SuppressWarnings("unused")
    void cleanDatabase() {
        approvalAuditEntryRepository.deleteAll();
        approvalRequestRepository.deleteAll();
    }

    @Test
    void should_createGetAndListRequest() throws Exception {
        String locationPath = createDraftRequest();

        mockMvc.perform(get(locationPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.requestedBy").value("alice"));

        mockMvc.perform(get("/requests?status=DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject").value("Laptop Purchase"));
    }

    @Test
    void should_runWorkflowEndToEnd_andCreateAuditEntries() throws Exception {
        String locationPath = createDraftRequest();

        mockMvc.perform(put(locationPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "Laptop Purchase v2",
                                  "description": "Need device plus docking station",
                                  "approver": "manager"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post(locationPath + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(post(locationPath + "/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "manager",
                                  "comment": "Please provide cost breakdown"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.latestComment").value("Please provide cost breakdown"));

        mockMvc.perform(post(locationPath + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(post(locationPath + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"manager\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get(locationPath + "/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("RETURNED"))
                .andExpect(jsonPath("$[0].comment").value("Please provide cost breakdown"))
                .andExpect(jsonPath("$[1].action").value("APPROVED"))
                .andExpect(jsonPath("$[1].comment").value(nullValue()));
    }

    @Test
    void should_returnStructuredValidationError_when_createPayloadIsInvalid() throws Exception {
        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "",
                                  "description": "Some description",
                                  "requestedBy": "alice",
                                  "approver": "manager"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("validation failed"))
                .andExpect(jsonPath("$.path").value("/requests"))
                .andExpect(jsonPath("$.validationErrors.subject").exists());
    }

    @Test
    void should_returnStructuredConflictError_when_transitionIsInvalid() throws Exception {
        String locationPath = createDraftRequest();

        mockMvc.perform(post(locationPath + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"manager\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("only submitted requests can be approved"))
                .andExpect(jsonPath("$.path").value(locationPath + "/approve"));
    }

    @Test
    void should_returnConflict_when_submitActorIsNotRequester() throws Exception {
        String locationPath = createDraftRequest();

        mockMvc.perform(post(locationPath + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"someone-else\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("only requester can submit this request"))
                .andExpect(jsonPath("$.path").value(locationPath + "/submit"));
    }

    @Test
    void should_returnConflict_when_approveActorIsNotAssignedApprover() throws Exception {
        String locationPath = createDraftRequest();

        mockMvc.perform(post(locationPath + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"alice\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(locationPath + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"another-manager\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("only the assigned approver can approve this request"))
                .andExpect(jsonPath("$.path").value(locationPath + "/approve"));
    }

    @Test
    void should_returnBadRequest_when_submitPayloadIsInvalid() throws Exception {
        String locationPath = createDraftRequest();

        mockMvc.perform(post(locationPath + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("validation failed"))
                .andExpect(jsonPath("$.validationErrors.actor").exists());
    }

    @Test
    void should_returnConflict_when_returnIsCalledBeforeSubmit() throws Exception {
        String locationPath = createDraftRequest();

        mockMvc.perform(post(locationPath + "/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "manager",
                                  "comment": "Please add details"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("only submitted requests can be returned"));
    }

    @Test
    void should_returnConflict_when_updatingApprovedRequest() throws Exception {
        String locationPath = createDraftRequest();

        mockMvc.perform(post(locationPath + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"alice\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(locationPath + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"manager\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put(locationPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "Post-approval edit",
                                  "description": "Should not be allowed",
                                  "approver": "manager"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("only draft or returned requests can be updated"));
    }

    @Test
    void should_returnEmptyAudit_when_noDecisionWasMade() throws Exception {
        String locationPath = createDraftRequest();

        mockMvc.perform(get(locationPath + "/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void should_filterListByStatusAndRequestedBy_caseInsensitive() throws Exception {
        createDraftRequest();

        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "Office Equipment",
                                  "description": "Dual monitors",
                                  "requestedBy": "bob",
                                  "approver": "lead"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/requests")
                        .param("status", "DRAFT")
                        .param("requestedBy", "ALICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestedBy").value("alice"));
    }

    @Test
    void should_returnStructuredBusinessValidationError_when_requesterEqualsApprover() throws Exception {
        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "Team Offsite",
                                  "description": "Travel approval",
                                  "requestedBy": "alice",
                                  "approver": "alice"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("requester and approver must be different users"))
                .andExpect(jsonPath("$.path").value("/requests"));
    }

    @Test
    void should_returnStructuredNotFoundError_when_requestDoesNotExist() throws Exception {
        UUID unknownId = UUID.randomUUID();
        String path = "/requests/" + unknownId;

        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("approval request not found: " + unknownId))
                .andExpect(jsonPath("$.path").value(path));
    }

    private String createDraftRequest() throws Exception {
        String location = mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "Laptop Purchase",
                                  "description": "Need replacement device",
                                  "requestedBy": "alice",
                                  "approver": "manager"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        return URI.create(location).getPath();
    }
}
