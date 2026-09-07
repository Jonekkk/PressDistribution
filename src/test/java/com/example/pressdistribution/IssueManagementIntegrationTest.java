package com.example.pressdistribution;

import com.example.pressdistribution.model.*;
import com.example.pressdistribution.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.seed.enabled=false",
    "app.bootstrap-admin.email=",
    "app.bootstrap-admin.full-name=",
    "app.bootstrap-admin.password="
})
class IssueManagementIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private ParishIssueRecordRepository parishIssueRecordRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Publication testPublication;
    private Publication testPublication2;
    private Issue testIssue;
    private Parish testParish;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user("admin@issue-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user("priest@issue-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        // Create test parish (needed for Parish Priest user)
        testParish = new Parish();
        testParish.setLocality("Test Locality");
        testParish.setName("Test Parish");
        testParish.setAddress("Test Address");
        testParish = parishRepository.save(testParish);

        // Create admin user
        User admin = new User();
        admin.setFullName("Test Admin");
        admin.setEmail("admin@issue-test.com");
        admin.setPasswordHash(passwordEncoder.encode("password"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("recovery"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        // Create parish priest user
        User priest = new User();
        priest.setFullName("Test Priest");
        priest.setEmail("priest@issue-test.com");
        priest.setPasswordHash(passwordEncoder.encode("password"));
        priest.setRecoveryCodeHash(passwordEncoder.encode("recovery"));
        priest.setRole(UserRole.PARISH_PRIEST);
        priest.setParish(testParish);
        priest.setActive(true);
        userRepository.save(priest);

        // Create test publications
        testPublication = new Publication();
        testPublication.setName("Alpha Publication");
        testPublication = publicationRepository.save(testPublication);

        testPublication2 = new Publication();
        testPublication2.setName("Beta Publication");
        testPublication2 = publicationRepository.save(testPublication2);

        // Create a test issue
        testIssue = new Issue();
        testIssue.setPublication(testPublication);
        testIssue.setIssueNumber("Issue 1");
        testIssue.setPublicationDate(LocalDate.of(2024, 3, 15));
        testIssue.setUnitPrice(new BigDecimal("5.50"));
        testIssue = issueRepository.save(testIssue);
    }

    @AfterEach
    void tearDown() {
        parishIssueRecordRepository.deleteAll();
        issueRepository.deleteAll();
        publicationRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
    }

    // --- Security: unauthenticated ---

    @Test
    void testUnauthenticatedGet_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/issues"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testUnauthenticatedPost_withCsrf_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "99")
                .param("publicationDate", "2024-01-01")
                .param("unitPrice", "1.00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testUnauthenticatedPost_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "99")
                .param("publicationDate", "2024-01-01")
                .param("unitPrice", "1.00"))
            .andExpect(status().isForbidden());
    }

    // --- Security: Parish Priest forbidden ---

    @Test
    void testParishPriest_getList_returns403() throws Exception {
        mockMvc.perform(get("/admin/issues")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_getNew_returns403() throws Exception {
        mockMvc.perform(get("/admin/issues/new")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_postCreate_returns403() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "99")
                .param("publicationDate", "2024-01-01")
                .param("unitPrice", "1.00"))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_getEdit_returns403() throws Exception {
        mockMvc.perform(get("/admin/issues/" + testIssue.getId() + "/edit")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_postUpdate_returns403() throws Exception {
        mockMvc.perform(post("/admin/issues/" + testIssue.getId())
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Hacked")
                .param("publicationDate", "2024-01-01")
                .param("unitPrice", "1.00"))
            .andExpect(status().isForbidden());
    }

    // --- List ---

    @Test
    void testAdminGetList_returns200WithIssues() throws Exception {
        mockMvc.perform(get("/admin/issues")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Alpha Publication")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Issue 1")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<table")));
    }

    @Test
    void testAdminGetList_emptyState() throws Exception {
        parishIssueRecordRepository.deleteAll();
        issueRepository.deleteAll();

        mockMvc.perform(get("/admin/issues")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("No issues found for the selected publication")));
    }

    @Test
    void testAdminGetList_multiColumnSorting() throws Exception {
        // Create issues for sorting verification:
        // Alpha Publication, 2024-03-15, "Issue 1" (already exists)
        // Alpha Publication, 2024-03-15, "Issue 2"
        // Alpha Publication, 2024-01-01, "Issue 3" (earlier date, should be after 2024-03-15 issues)
        // Beta Publication, 2024-06-01, "Issue A"

        Issue issue2 = new Issue();
        issue2.setPublication(testPublication);
        issue2.setIssueNumber("Issue 2");
        issue2.setPublicationDate(LocalDate.of(2024, 3, 15));
        issue2.setUnitPrice(new BigDecimal("3.00"));
        issueRepository.save(issue2);

        Issue issue3 = new Issue();
        issue3.setPublication(testPublication);
        issue3.setIssueNumber("Issue 3");
        issue3.setPublicationDate(LocalDate.of(2024, 1, 1));
        issue3.setUnitPrice(new BigDecimal("4.00"));
        issueRepository.save(issue3);

        Issue issueA = new Issue();
        issueA.setPublication(testPublication2);
        issueA.setIssueNumber("Issue A");
        issueA.setPublicationDate(LocalDate.of(2024, 6, 1));
        issueA.setUnitPrice(new BigDecimal("2.00"));
        issueRepository.save(issueA);

        String html = mockMvc.perform(get("/admin/issues")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Expected order:
        // Alpha Publication / 2024-03-15 / Issue 1
        // Alpha Publication / 2024-03-15 / Issue 2
        // Alpha Publication / 2024-01-01 / Issue 3
        // Beta Publication / 2024-06-01 / Issue A

        int posIssue1 = html.indexOf("Issue 1");
        int posIssue2 = html.indexOf("Issue 2");
        int posIssue3 = html.indexOf("Issue 3");
        int posIssueA = html.indexOf("Issue A");

        assertThat(posIssue1).isLessThan(posIssue2);
        assertThat(posIssue2).isLessThan(posIssue3);
        assertThat(posIssue3).isLessThan(posIssueA);
    }

    // --- Create ---

    @Test
    void testCreateIssue_success() throws Exception {
        long issueCountBefore = issueRepository.count();

        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "2024/01")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "3.50"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/issues"))
            .andExpect(flash().attribute("successMessage", "Issue created successfully"));

        assertThat(issueRepository.count()).isEqualTo(issueCountBefore + 1);
    }

    @Test
    void testCreateIssue_trimmingWhitespace() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "  Trimmed  ")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "3.50"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/issues"));

        // Verify the issue number was trimmed
        List<Issue> issues = issueRepository.findAllSortedForList();
        assertThat(issues).anyMatch(i -> "Trimmed".equals(i.getIssueNumber()));
    }

    @Test
    void testCreateIssue_blankIssueNumber_rejected() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "3.50"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Issue number is required")));
    }

    @Test
    void testCreateIssue_issueNumberTooLong_rejected() throws Exception {
        String longNumber = "A".repeat(101);
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", longNumber)
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "3.50"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Issue number must not exceed 100 characters")));
    }

    @Test
    void testCreateIssue_invalidPattern_rejected() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Issue#1!")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "3.50"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Issue number may only contain letters, digits, spaces, slashes and hyphens")));
    }

    @Test
    void testCreateIssue_negativePrice_rejected() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Valid 1")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "-1.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Unit price must be greater than zero")));
    }

    @Test
    void testCreateIssue_tooManyDecimalPlaces_rejected() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Valid 2")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "1.234"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Unit price must have at most 8 integer digits and 2 decimal places")));
    }

    @Test
    void testCreateIssue_duplicateCaseInsensitive_rejected() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "ISSUE 1")  // existing is "Issue 1"
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "3.50"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("An issue with this number already exists for the selected publication")));
    }

    @Test
    void testCreateIssue_missingPublicationId_rejected() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueNumber", "Valid 3")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "3.50"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Publication is required")));
    }

    @Test
    void testCreateIssue_nonExistentPublicationId_rejected() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", "99999")
                .param("issueNumber", "Valid 4")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "3.50"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Selected publication does not exist")));
    }

    @Test
    void testCreateIssue_missingPublicationDate_rejected() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Valid 5")
                .param("unitPrice", "3.50"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Publication date is required")));
    }

    // --- Edit ---

    @Test
    void testEditForm_prePopulated() throws Exception {
        mockMvc.perform(get("/admin/issues/" + testIssue.getId() + "/edit")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Issue 1")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("5.50")));
    }

    @Test
    void testUpdateIssue_success() throws Exception {
        mockMvc.perform(post("/admin/issues/" + testIssue.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Updated Issue")
                .param("publicationDate", "2024-07-01")
                .param("unitPrice", "6.00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/issues"))
            .andExpect(flash().attribute("successMessage", "Issue updated successfully"));

        Issue updated = issueRepository.findById(testIssue.getId()).orElseThrow();
        assertThat(updated.getIssueNumber()).isEqualTo("Updated Issue");
        assertThat(updated.getUnitPrice()).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(updated.getPublicationDate()).isEqualTo(LocalDate.of(2024, 7, 1));
    }

    @Test
    void testUpdateIssue_duplicateRejectionExcludingSelf() throws Exception {
        // Create another issue for duplicate testing
        Issue other = new Issue();
        other.setPublication(testPublication);
        other.setIssueNumber("Other Issue");
        other.setPublicationDate(LocalDate.of(2024, 5, 1));
        other.setUnitPrice(new BigDecimal("2.00"));
        issueRepository.save(other);

        // Try to update testIssue to have same number as "Other Issue"
        mockMvc.perform(post("/admin/issues/" + testIssue.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "OTHER ISSUE")
                .param("publicationDate", "2024-07-01")
                .param("unitPrice", "6.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("An issue with this number already exists for the selected publication")));

        // Verify unchanged
        Issue unchanged = issueRepository.findById(testIssue.getId()).orElseThrow();
        assertThat(unchanged.getIssueNumber()).isEqualTo("Issue 1");
    }

    @Test
    void testUpdateIssue_sameValuesAccepted() throws Exception {
        mockMvc.perform(post("/admin/issues/" + testIssue.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Issue 1")
                .param("publicationDate", "2024-03-15")
                .param("unitPrice", "5.50"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/issues"));
    }

    @Test
    void testEditNonExistentIssue_returns404() throws Exception {
        mockMvc.perform(get("/admin/issues/99999/edit")
                .with(ADMIN_USER))
            .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateNonExistentIssue_returns404() throws Exception {
        mockMvc.perform(post("/admin/issues/99999")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Valid")
                .param("publicationDate", "2024-01-01")
                .param("unitPrice", "1.00"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateIssue_validationReApplied() throws Exception {
        mockMvc.perform(post("/admin/issues/" + testIssue.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Invalid#!")
                .param("publicationDate", "2024-07-01")
                .param("unitPrice", "6.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Issue number may only contain letters, digits, spaces, slashes and hyphens")));
    }

    // --- Delete ---

    @Test
    void testDeleteConfirmation_displaysIssueInfo() throws Exception {
        mockMvc.perform(get("/admin/issues/" + testIssue.getId() + "/delete")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Alpha Publication")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Issue 1")));
    }

    @Test
    void testDeleteIssue_success_noRecords() throws Exception {
        long issueCountBefore = issueRepository.count();

        mockMvc.perform(post("/admin/issues/" + testIssue.getId() + "/delete")
                .with(ADMIN_USER)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/issues"))
            .andExpect(flash().attribute("successMessage", "Issue deleted successfully"));

        assertThat(issueRepository.count()).isEqualTo(issueCountBefore - 1);
        assertThat(issueRepository.findById(testIssue.getId())).isEmpty();
    }

    @Test
    void testDeleteIssue_rejectedWhenRecordsExist() throws Exception {
        // Create ParishIssueRecord: Parish → Publication → Issue → Record
        ParishIssueRecord record = new ParishIssueRecord();
        record.setParish(testParish);
        record.setIssue(testIssue);
        record.setDeliveredCopies(0);
        record.setReturnedCopies(0);
        record.setPaidAmount(BigDecimal.ZERO);
        parishIssueRecordRepository.save(record);

        long issueCountBefore = issueRepository.count();

        mockMvc.perform(post("/admin/issues/" + testIssue.getId() + "/delete")
                .with(ADMIN_USER)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("This issue cannot be deleted because it has existing parish records.")));

        // Issue unchanged
        assertThat(issueRepository.count()).isEqualTo(issueCountBefore);
        assertThat(issueRepository.findById(testIssue.getId())).isPresent();
    }

    @Test
    void testDeleteNonExistentIssue_get_returns404() throws Exception {
        mockMvc.perform(get("/admin/issues/99999/delete")
                .with(ADMIN_USER))
            .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteNonExistentIssue_post_returns404() throws Exception {
        mockMvc.perform(post("/admin/issues/99999/delete")
                .with(ADMIN_USER)
                .with(csrf()))
            .andExpect(status().isNotFound());
    }

    // --- Audit ---

    @Test
    void testAudit_createSuccess_producesAuditLog() throws Exception {
        auditLogRepository.deleteAll();

        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Audit Create")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "3.50"))
            .andExpect(status().is3xxRedirection());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("Issue created") &&
            log.getMessage().contains("Alpha Publication") &&
            log.getMessage().contains("Audit Create"));
    }

    @Test
    void testAudit_updateSuccess_producesAuditLog() throws Exception {
        auditLogRepository.deleteAll();

        mockMvc.perform(post("/admin/issues/" + testIssue.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Audit Update")
                .param("publicationDate", "2024-07-01")
                .param("unitPrice", "6.00"))
            .andExpect(status().is3xxRedirection());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("Issue updated") &&
            log.getMessage().contains("Alpha Publication") &&
            log.getMessage().contains("Audit Update"));
    }

    @Test
    void testAudit_deleteSuccess_producesAuditLog() throws Exception {
        auditLogRepository.deleteAll();

        mockMvc.perform(post("/admin/issues/" + testIssue.getId() + "/delete")
                .with(ADMIN_USER)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("Issue deleted") &&
            log.getMessage().contains("Alpha Publication") &&
            log.getMessage().contains("Issue 1"));
    }

    @Test
    void testAudit_validationFailure_noAuditLog() throws Exception {
        auditLogRepository.deleteAll();

        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "3.50"))
            .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).isEmpty();
    }

    @Test
    void testAudit_rejectedDeletion_noAuditLog() throws Exception {
        // Create ParishIssueRecord to block deletion
        ParishIssueRecord record = new ParishIssueRecord();
        record.setParish(testParish);
        record.setIssue(testIssue);
        record.setDeliveredCopies(0);
        record.setReturnedCopies(0);
        record.setPaidAmount(BigDecimal.ZERO);
        parishIssueRecordRepository.save(record);

        auditLogRepository.deleteAll();

        mockMvc.perform(post("/admin/issues/" + testIssue.getId() + "/delete")
                .with(ADMIN_USER)
                .with(csrf()))
            .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).isEmpty();
    }
}
