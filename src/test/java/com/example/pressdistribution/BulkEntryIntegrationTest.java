package com.example.pressdistribution;

import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.ParishIssueRecord;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import com.example.pressdistribution.repository.UserRepository;
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
class BulkEntryIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ParishIssueRecordRepository parishIssueRecordRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Parish parish1;
    private Parish parish2;
    private Parish parish3;
    private Publication publication;
    private Issue issue;
    private ParishIssueRecord existingRecord;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user("admin@bulk-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user("priest@bulk-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        // Create parishes
        parish1 = new Parish();
        parish1.setLocality("Alpha City");
        parish1.setName("St. Mary");
        parish1 = parishRepository.save(parish1);

        parish2 = new Parish();
        parish2.setLocality("Beta Town");
        parish2.setName("St. John");
        parish2 = parishRepository.save(parish2);

        parish3 = new Parish();
        parish3.setLocality("Gamma Village");
        parish3.setName("St. Peter");
        parish3 = parishRepository.save(parish3);

        // Create admin user
        User admin = new User();
        admin.setFullName("Bulk Test Admin");
        admin.setEmail("admin@bulk-test.com");
        admin.setPasswordHash(passwordEncoder.encode("password"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("recovery"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        // Create priest user assigned to parish1
        User priest = new User();
        priest.setFullName("Bulk Test Priest");
        priest.setEmail("priest@bulk-test.com");
        priest.setPasswordHash(passwordEncoder.encode("password"));
        priest.setRecoveryCodeHash(passwordEncoder.encode("recovery"));
        priest.setRole(UserRole.PARISH_PRIEST);
        priest.setParish(parish1);
        priest.setActive(true);
        userRepository.save(priest);

        // Create publication and issue
        publication = new Publication();
        publication.setName("Weekly Gazette");
        publication = publicationRepository.save(publication);

        issue = new Issue();
        issue.setPublication(publication);
        issue.setIssueNumber("2024/10");
        issue.setPublicationDate(LocalDate.of(2024, 10, 1));
        issue.setUnitPrice(new BigDecimal("2.50"));
        issue = issueRepository.save(issue);

        // Create an existing record for parish1
        existingRecord = new ParishIssueRecord();
        existingRecord.setParish(parish1);
        existingRecord.setIssue(issue);
        existingRecord.setDeliveredCopies(20);
        existingRecord.setReturnedCopies(5);
        existingRecord.setPaidAmount(new BigDecimal("30.00"));
        existingRecord = parishIssueRecordRepository.save(existingRecord);
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        parishIssueRecordRepository.deleteAll();
        issueRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
        publicationRepository.deleteAll();
    }

    // --- Test 1: Successful bulk save with mixed new and existing records ---

    @Test
    void successfulBulkSave_mixedNewAndExisting() throws Exception {
        long recordCountBefore = parishIssueRecordRepository.count();

        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish1.getId().toString())
                .param("rows[0].deliveredCopies", "25")
                .param("rows[0].returnedCopies", "3")
                .param("rows[0].paidAmount", "50.00")
                .param("rows[1].parishId", parish2.getId().toString())
                .param("rows[1].deliveredCopies", "10")
                .param("rows[1].returnedCopies", "2")
                .param("rows[1].paidAmount", "5.00")
                .param("rows[2].parishId", parish3.getId().toString())
                .param("rows[2].deliveredCopies", "")
                .param("rows[2].returnedCopies", "")
                .param("rows[2].paidAmount", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/records/bulk"))
            .andExpect(flash().attribute("successMessage", "Bulk entry saved successfully."));

        // Verify DB state: parish1 updated, parish2 created, parish3 no record
        assertThat(parishIssueRecordRepository.count()).isEqualTo(recordCountBefore + 1);

        ParishIssueRecord record1 = parishIssueRecordRepository.findByParishAndIssue(parish1, issue).orElseThrow();
        assertThat(record1.getDeliveredCopies()).isEqualTo(25);
        assertThat(record1.getReturnedCopies()).isEqualTo(3);
        assertThat(record1.getPaidAmount()).isEqualByComparingTo("50.00");

        ParishIssueRecord record2 = parishIssueRecordRepository.findByParishAndIssue(parish2, issue).orElseThrow();
        assertThat(record2.getDeliveredCopies()).isEqualTo(10);
        assertThat(record2.getReturnedCopies()).isEqualTo(2);
        assertThat(record2.getPaidAmount()).isEqualByComparingTo("5.00");

        assertThat(parishIssueRecordRepository.findByParishAndIssue(parish3, issue)).isEmpty();
    }

    // --- Test 2: AuditLog entries per changed row ---

    @Test
    void auditLogEntriesPerChangedRow() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish1.getId().toString())
                .param("rows[0].deliveredCopies", "30")
                .param("rows[0].returnedCopies", "5")
                .param("rows[0].paidAmount", "30.00")
                .param("rows[1].parishId", parish2.getId().toString())
                .param("rows[1].deliveredCopies", "15")
                .param("rows[1].returnedCopies", "0")
                .param("rows[1].paidAmount", "10.00"))
            .andExpect(status().is3xxRedirection());

        // Two changed rows: parish1 updated (delivered changed 20->30), parish2 created
        List<AuditLog> logs = auditLogRepository.findAll();
        long newEntries = logs.size() - auditCountBefore;
        assertThat(newEntries).isEqualTo(2);

        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("updated") &&
            log.getMessage().contains("Alpha City") &&
            log.getMessage().contains("St. Mary") &&
            log.getMessage().contains("Weekly Gazette") &&
            log.getMessage().contains("2024/10"));

        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("created") &&
            log.getMessage().contains("Beta Town") &&
            log.getMessage().contains("St. John") &&
            log.getMessage().contains("Weekly Gazette") &&
            log.getMessage().contains("2024/10"));
    }

    // --- Test 3: Blank rows not creating records ---

    @Test
    void blankRowsNotCreatingRecords() throws Exception {
        long recordCountBefore = parishIssueRecordRepository.count();

        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish2.getId().toString())
                .param("rows[0].deliveredCopies", "")
                .param("rows[0].returnedCopies", "")
                .param("rows[0].paidAmount", "")
                .param("rows[1].parishId", parish3.getId().toString())
                .param("rows[1].deliveredCopies", "")
                .param("rows[1].returnedCopies", "")
                .param("rows[1].paidAmount", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/records/bulk"));

        // No new records created
        assertThat(parishIssueRecordRepository.count()).isEqualTo(recordCountBefore);
        assertThat(parishIssueRecordRepository.findByParishAndIssue(parish2, issue)).isEmpty();
        assertThat(parishIssueRecordRepository.findByParishAndIssue(parish3, issue)).isEmpty();
    }

    // --- Test 4: Existing records preserved on blank ---

    @Test
    void existingRecordsPreservedOnBlank() throws Exception {
        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish1.getId().toString())
                .param("rows[0].deliveredCopies", "")
                .param("rows[0].returnedCopies", "")
                .param("rows[0].paidAmount", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/records/bulk"));

        // Existing record unchanged
        ParishIssueRecord record = parishIssueRecordRepository.findByParishAndIssue(parish1, issue).orElseThrow();
        assertThat(record.getDeliveredCopies()).isEqualTo(20);
        assertThat(record.getReturnedCopies()).isEqualTo(5);
        assertThat(record.getPaidAmount()).isEqualByComparingTo("30.00");
    }

    // --- Test 5: Validation failure prevents all persistence ---

    @Test
    void validationFailurePreventsAllPersistence() throws Exception {
        long recordCountBefore = parishIssueRecordRepository.count();
        long auditCountBefore = auditLogRepository.count();

        // Submit one valid row for parish2 and one invalid row for parish3 (returned > delivered)
        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish2.getId().toString())
                .param("rows[0].deliveredCopies", "10")
                .param("rows[0].returnedCopies", "2")
                .param("rows[0].paidAmount", "5.00")
                .param("rows[1].parishId", parish3.getId().toString())
                .param("rows[1].deliveredCopies", "5")
                .param("rows[1].returnedCopies", "10")
                .param("rows[1].paidAmount", "3.00"))
            .andExpect(status().isOk());

        // No new records or audit entries created
        assertThat(parishIssueRecordRepository.count()).isEqualTo(recordCountBefore);
        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
        assertThat(parishIssueRecordRepository.findByParishAndIssue(parish2, issue)).isEmpty();
    }

    // --- Test 6: Returned exceeds delivered rejected ---

    @Test
    void returnedExceedsDelivered_rejected() throws Exception {
        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish2.getId().toString())
                .param("rows[0].deliveredCopies", "5")
                .param("rows[0].returnedCopies", "10")
                .param("rows[0].paidAmount", "0.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("Returned copies must not exceed delivered copies")));
    }

    // --- Test 7: Negative values rejected ---

    @Test
    void negativeValues_rejected() throws Exception {
        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish2.getId().toString())
                .param("rows[0].deliveredCopies", "-5")
                .param("rows[0].returnedCopies", "0")
                .param("rows[0].paidAmount", "0.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("Delivered copies must not be negative")));

        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish2.getId().toString())
                .param("rows[0].deliveredCopies", "10")
                .param("rows[0].returnedCopies", "-3")
                .param("rows[0].paidAmount", "0.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("Returned copies must not be negative")));

        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish2.getId().toString())
                .param("rows[0].deliveredCopies", "10")
                .param("rows[0].returnedCopies", "0")
                .param("rows[0].paidAmount", "-1.50"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("Paid amount must not be negative")));
    }

    // --- Test 8: Non-numeric values rejected ---

    @Test
    void nonNumericValues_rejected() throws Exception {
        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish2.getId().toString())
                .param("rows[0].deliveredCopies", "abc")
                .param("rows[0].returnedCopies", "0")
                .param("rows[0].paidAmount", "0.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("Delivered copies must be a valid integer")));

        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish2.getId().toString())
                .param("rows[0].deliveredCopies", "10")
                .param("rows[0].returnedCopies", "xyz")
                .param("rows[0].paidAmount", "0.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("Returned copies must be a valid integer")));

        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish2.getId().toString())
                .param("rows[0].deliveredCopies", "10")
                .param("rows[0].returnedCopies", "0")
                .param("rows[0].paidAmount", "not_a_number"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("Paid amount must be a valid decimal number")));
    }

    // --- Test 9: Missing CSRF returns 403 ---

    @Test
    void missingCsrf_returns403() throws Exception {
        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish2.getId().toString())
                .param("rows[0].deliveredCopies", "10")
                .param("rows[0].returnedCopies", "2")
                .param("rows[0].paidAmount", "5.00"))
            .andExpect(status().isForbidden());
    }

    // --- Test 10: Parish Priest access returns 403 ---

    @Test
    void priestAccess_returns403() throws Exception {
        // GET
        mockMvc.perform(get("/admin/records/bulk")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());

        // POST
        mockMvc.perform(post("/admin/records/bulk")
                .with(PRIEST_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish1.getId().toString())
                .param("rows[0].deliveredCopies", "10")
                .param("rows[0].returnedCopies", "2")
                .param("rows[0].paidAmount", "5.00"))
            .andExpect(status().isForbidden());
    }

    // --- Test 11: Unauthenticated access redirects to login ---

    @Test
    void unauthenticated_redirectsToLogin() throws Exception {
        // GET without auth
        mockMvc.perform(get("/admin/records/bulk"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));

        // POST without auth (with CSRF to test redirect, not 403 for missing CSRF)
        mockMvc.perform(post("/admin/records/bulk")
                .with(csrf())
                .param("issueId", issue.getId().toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    // --- Test 12: Unchanged rows get no audit ---

    @Test
    void unchangedRowsNoAudit() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        // Submit the exact same values as the existing record for parish1
        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish1.getId().toString())
                .param("rows[0].deliveredCopies", "20")
                .param("rows[0].returnedCopies", "5")
                .param("rows[0].paidAmount", "30.00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/records/bulk"));

        // No new audit entries
        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);

        // Record values unchanged
        ParishIssueRecord record = parishIssueRecordRepository.findByParishAndIssue(parish1, issue).orElseThrow();
        assertThat(record.getDeliveredCopies()).isEqualTo(20);
        assertThat(record.getReturnedCopies()).isEqualTo(5);
        assertThat(record.getPaidAmount()).isEqualByComparingTo("30.00");
    }
}
