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
import org.springframework.mock.web.MockHttpServletResponse;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ReportIntegrationTest {

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
    private ParishIssueRecordRepository recordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user("admin@report-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user("priest@report-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    private Parish parish1;
    private Parish parish2;
    private Publication publication1;
    private Publication publication2;
    private Issue issue1;
    private Issue issue2;
    private Issue issue3;
    private ParishIssueRecord record1;
    private ParishIssueRecord record2;
    private ParishIssueRecord record3;

    @BeforeEach
    void setUp() {
        // Create parishes
        parish1 = new Parish();
        parish1.setLocality("Locality A");
        parish1.setName("Parish One");
        parish1 = parishRepository.save(parish1);

        parish2 = new Parish();
        parish2.setLocality("Locality B");
        parish2.setName("Parish Two");
        parish2 = parishRepository.save(parish2);

        // Create users
        User admin = new User();
        admin.setFullName("Report Admin");
        admin.setEmail("admin@report-test.com");
        admin.setPasswordHash(passwordEncoder.encode("password"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("recovery"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        User priest = new User();
        priest.setFullName("Report Priest");
        priest.setEmail("priest@report-test.com");
        priest.setPasswordHash(passwordEncoder.encode("password"));
        priest.setRecoveryCodeHash(passwordEncoder.encode("recovery"));
        priest.setRole(UserRole.PARISH_PRIEST);
        priest.setParish(parish1);
        priest.setActive(true);
        userRepository.save(priest);

        // Create publications
        publication1 = new Publication();
        publication1.setName("Publication Alpha");
        publication1 = publicationRepository.save(publication1);

        publication2 = new Publication();
        publication2.setName("Publication Beta");
        publication2 = publicationRepository.save(publication2);

        // Create issues
        issue1 = new Issue();
        issue1.setPublication(publication1);
        issue1.setIssueNumber("2024/01");
        issue1.setPublicationDate(LocalDate.of(2024, 1, 15));
        issue1.setUnitPrice(new BigDecimal("2.50"));
        issue1 = issueRepository.save(issue1);

        issue2 = new Issue();
        issue2.setPublication(publication1);
        issue2.setIssueNumber("2024/02");
        issue2.setPublicationDate(LocalDate.of(2024, 2, 20));
        issue2.setUnitPrice(new BigDecimal("3.00"));
        issue2 = issueRepository.save(issue2);

        issue3 = new Issue();
        issue3.setPublication(publication2);
        issue3.setIssueNumber("2024/01");
        issue3.setPublicationDate(LocalDate.of(2024, 1, 10));
        issue3.setUnitPrice(new BigDecimal("1.50"));
        issue3 = issueRepository.save(issue3);

        // Create parish issue records
        record1 = new ParishIssueRecord();
        record1.setParish(parish1);
        record1.setIssue(issue1);
        record1.setDeliveredCopies(10);
        record1.setReturnedCopies(2);
        record1.setPaidAmount(new BigDecimal("15.00"));
        record1 = recordRepository.save(record1);

        record2 = new ParishIssueRecord();
        record2.setParish(parish1);
        record2.setIssue(issue2);
        record2.setDeliveredCopies(5);
        record2.setReturnedCopies(1);
        record2.setPaidAmount(new BigDecimal("8.00"));
        record2 = recordRepository.save(record2);

        record3 = new ParishIssueRecord();
        record3.setParish(parish2);
        record3.setIssue(issue1);
        record3.setDeliveredCopies(20);
        record3.setReturnedCopies(5);
        record3.setPaidAmount(new BigDecimal("30.00"));
        record3 = recordRepository.save(record3);
    }

    @AfterEach
    void tearDown() {
        recordRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        issueRepository.deleteAll();
        publicationRepository.deleteAll();
        parishRepository.deleteAll();
    }

    // --- Test 1: adminPublicationReportShowsAllRecords ---

    @Test
    void adminPublicationReportShowsAllRecords() throws Exception {
        String content = mockMvc.perform(get("/reports/publications")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Verify all 3 records appear
        assertThat(content).contains("Publication Alpha");
        assertThat(content).contains("Publication Beta");
        assertThat(content).contains("Locality A");
        assertThat(content).contains("Parish One");
        assertThat(content).contains("Locality B");
        assertThat(content).contains("Parish Two");

        // Verify all 11 columns headers exist
        assertThat(content).contains("Publication");
        assertThat(content).contains("Issue");
        assertThat(content).contains("Issue date");
        assertThat(content).contains("Parish locality");
        assertThat(content).contains("Parish name");
        assertThat(content).contains("Delivered copies");
        assertThat(content).contains("Returned copies");
        assertThat(content).contains("Sold copies");
        assertThat(content).contains("Unit price");
        assertThat(content).contains("Amount due");
        assertThat(content).contains("Paid amount");
    }

    // --- Test 2: adminPublicationReportWithDateFilter ---

    @Test
    void adminPublicationReportWithDateFilter() throws Exception {
        String content = mockMvc.perform(get("/reports/publications")
                .param("dateFrom", "2024-02-01")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Only issue2 (2024-02-20) should match
        assertThat(content).contains("2024/02");
        // issue1 (2024-01-15) and issue3 (2024-01-10) should NOT appear as data rows
        // But publication names may still appear in dropdowns, so we check specifically for record data
        assertThat(content).contains("5"); // delivered for record2
        assertThat(content).doesNotContain("2024-01-15"); // issue1 date not in records
    }

    // --- Test 3: adminPublicationReportDateRangeValidation ---

    @Test
    void adminPublicationReportDateRangeValidation() throws Exception {
        String content = mockMvc.perform(get("/reports/publications")
                .param("dateFrom", "2024-03-01")
                .param("dateTo", "2024-01-01")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Should show validation error
        assertThat(content).containsIgnoringCase("date from must not be later than date to");
    }

    // --- Test 4: adminParishReportWithTotals ---

    @Test
    void adminParishReportWithTotals() throws Exception {
        String content = mockMvc.perform(get("/reports/parishes")
                .param("parishId", parish1.getId().toString())
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Parish1 has record1 (delivered=10, returned=2, sold=8, amountDue=8*2.50=20.00, paid=15.00)
        // and record2 (delivered=5, returned=1, sold=4, amountDue=4*3.00=12.00, paid=8.00)
        // Totals: delivered=15, returned=3, sold=12, amountDue=32.00, paid=23.00
        assertThat(content).contains("Publication Alpha");
        assertThat(content).contains("2024/01");
        assertThat(content).contains("2024/02");

        // Verify totals are displayed
        assertThat(content).contains("15"); // total delivered
        assertThat(content).contains("32.00"); // total amount due
        assertThat(content).contains("23.00"); // total paid
    }

    // --- Test 5: adminParishReportRequiresParish ---

    @Test
    void adminParishReportRequiresParish() throws Exception {
        mockMvc.perform(get("/reports/parishes")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(model().attribute("showResults", false));
    }

    // --- Test 6: priestMyParishReportExcludesPaidAmount ---

    @Test
    void priestMyParishReportExcludesPaidAmount() throws Exception {
        String content = mockMvc.perform(get("/reports/my-parish")
                .with(PRIEST_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Should not contain "Paid amount" column header
        assertThat(content).doesNotContain("Paid amount");

        // Should show own parish data (parish1 records)
        assertThat(content).contains("Publication Alpha");
        assertThat(content).contains("2024/01");
        assertThat(content).contains("2024/02");
    }

    // --- Test 7: priestMyParishReportDateFilter ---

    @Test
    void priestMyParishReportDateFilter() throws Exception {
        String content = mockMvc.perform(get("/reports/my-parish")
                .param("dateFrom", "2024-02-01")
                .with(PRIEST_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Only issue2 (2024-02-20) should match for parish1
        assertThat(content).contains("2024/02");
        assertThat(content).doesNotContain("2024-01-15"); // issue1 date
    }

    // --- Test 8: priestCannotAccessPublicationReport ---

    @Test
    void priestCannotAccessPublicationReport() throws Exception {
        mockMvc.perform(get("/reports/publications")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    // --- Test 9: priestCannotAccessParishReport ---

    @Test
    void priestCannotAccessParishReport() throws Exception {
        mockMvc.perform(get("/reports/parishes")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    // --- Test 10: adminCannotAccessMyParishReport ---

    @Test
    void adminCannotAccessMyParishReport() throws Exception {
        mockMvc.perform(get("/reports/my-parish")
                .with(ADMIN_USER))
            .andExpect(status().isForbidden());
    }

    // --- Test 11: adminPublicationCsvExport ---

    @Test
    void adminPublicationCsvExport() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/reports/publications/export")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        // Verify content type
        assertThat(response.getContentType()).contains("text/csv");

        // Verify attachment header
        assertThat(response.getHeader("Content-Disposition")).contains("attachment; filename=");

        // Verify UTF-8 BOM (first 3 bytes)
        byte[] bytes = response.getContentAsByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);

        // Verify "Paid amount" header column is present for admin
        String csvContent = response.getContentAsString();
        assertThat(csvContent).contains("Paid amount");
    }

    // --- Test 12: adminParishCsvExportIncludesTotalsRow ---

    @Test
    void adminParishCsvExportIncludesTotalsRow() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/reports/parishes/export")
                .param("parishId", parish1.getId().toString())
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        String csvContent = response.getContentAsString();

        // Should include "Totals" row
        assertThat(csvContent).contains("Totals");

        // Verify content type
        assertThat(response.getContentType()).contains("text/csv");
    }

    // --- Test 13: priestMyParishCsvExcludesPaidAmount ---

    @Test
    void priestMyParishCsvExcludesPaidAmount() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/reports/my-parish/export")
                .with(PRIEST_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        String csvContent = response.getContentAsString();

        // Should NOT have "Paid amount" column
        assertThat(csvContent).doesNotContain("Paid amount");

        // Should contain parish1 record data
        assertThat(csvContent).contains("Publication Alpha");
    }

    // --- Test 14: csvRfc4180Escaping ---

    @Test
    void csvRfc4180Escaping() throws Exception {
        // Create a publication with comma and quote in name
        Publication specialPub = new Publication();
        specialPub.setName("Press, \"Daily\"");
        specialPub = publicationRepository.save(specialPub);

        Issue specialIssue = new Issue();
        specialIssue.setPublication(specialPub);
        specialIssue.setIssueNumber("S1");
        specialIssue.setPublicationDate(LocalDate.of(2024, 3, 1));
        specialIssue.setUnitPrice(new BigDecimal("1.00"));
        specialIssue = issueRepository.save(specialIssue);

        ParishIssueRecord specialRecord = new ParishIssueRecord();
        specialRecord.setParish(parish1);
        specialRecord.setIssue(specialIssue);
        specialRecord.setDeliveredCopies(3);
        specialRecord.setReturnedCopies(0);
        specialRecord.setPaidAmount(new BigDecimal("2.00"));
        recordRepository.save(specialRecord);

        MockHttpServletResponse response = mockMvc.perform(get("/reports/publications/export")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        String csvContent = response.getContentAsString();

        // RFC 4180: comma and quotes require double-quote wrapping, internal quotes doubled
        // "Press, ""Daily""" is the expected escaped form
        assertThat(csvContent).contains("\"Press, \"\"Daily\"\"\"");
    }

    // --- Test 15: csvFormulaInjectionProtection ---

    @Test
    void csvFormulaInjectionProtection() throws Exception {
        // Create a publication with name starting with "="
        Publication formulaPub = new Publication();
        formulaPub.setName("=SUM(A1:A10)");
        formulaPub = publicationRepository.save(formulaPub);

        Issue formulaIssue = new Issue();
        formulaIssue.setPublication(formulaPub);
        formulaIssue.setIssueNumber("F1");
        formulaIssue.setPublicationDate(LocalDate.of(2024, 4, 1));
        formulaIssue.setUnitPrice(new BigDecimal("1.00"));
        formulaIssue = issueRepository.save(formulaIssue);

        ParishIssueRecord formulaRecord = new ParishIssueRecord();
        formulaRecord.setParish(parish1);
        formulaRecord.setIssue(formulaIssue);
        formulaRecord.setDeliveredCopies(1);
        formulaRecord.setReturnedCopies(0);
        formulaRecord.setPaidAmount(new BigDecimal("1.00"));
        recordRepository.save(formulaRecord);

        MockHttpServletResponse response = mockMvc.perform(get("/reports/publications/export")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        String csvContent = response.getContentAsString();

        // Formula injection protection: value starting with "=" should be prefixed with apostrophe
        assertThat(csvContent).contains("'=SUM(A1:A10)");
    }

    // --- Test 16: reportsCreateNoAuditLogEntries ---

    @Test
    void reportsCreateNoAuditLogEntries() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        // Access various report pages
        mockMvc.perform(get("/reports/publications").with(ADMIN_USER))
            .andExpect(status().isOk());
        mockMvc.perform(get("/reports/parishes")
                .param("parishId", parish1.getId().toString())
                .with(ADMIN_USER))
            .andExpect(status().isOk());
        mockMvc.perform(get("/reports/my-parish").with(PRIEST_USER))
            .andExpect(status().isOk());

        // Access CSV exports
        mockMvc.perform(get("/reports/publications/export").with(ADMIN_USER))
            .andExpect(status().isOk());
        mockMvc.perform(get("/reports/parishes/export")
                .param("parishId", parish1.getId().toString())
                .with(ADMIN_USER))
            .andExpect(status().isOk());
        mockMvc.perform(get("/reports/my-parish/export").with(PRIEST_USER))
            .andExpect(status().isOk());

        long auditCountAfter = auditLogRepository.count();
        assertThat(auditCountAfter).isEqualTo(auditCountBefore);
    }

    // --- Test 17: csvExportIncludesAllRowsNotJustPage ---

    @Test
    void csvExportIncludesAllRowsNotJustPage() throws Exception {
        // Create more than 25 records (page size) to verify CSV exports all
        for (int i = 0; i < 26; i++) {
            Publication pub = new Publication();
            pub.setName("BulkPub " + String.format("%03d", i));
            pub = publicationRepository.save(pub);

            Issue issue = new Issue();
            issue.setPublication(pub);
            issue.setIssueNumber("B" + i);
            issue.setPublicationDate(LocalDate.of(2024, 5, 1));
            issue.setUnitPrice(new BigDecimal("1.00"));
            issue = issueRepository.save(issue);

            ParishIssueRecord record = new ParishIssueRecord();
            record.setParish(parish1);
            record.setIssue(issue);
            record.setDeliveredCopies(1);
            record.setReturnedCopies(0);
            record.setPaidAmount(new BigDecimal("1.00"));
            recordRepository.save(record);
        }

        MockHttpServletResponse response = mockMvc.perform(get("/reports/publications/export")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        String csvContent = response.getContentAsString();

        // Count data rows (total records: 3 original + 26 bulk = 29)
        // CSV has header + 29 data rows = 30 lines minimum
        String[] lines = csvContent.split("\r\n");
        // First line is header, rest are data rows
        // We should have at least 30 lines (1 header + 29 data rows)
        assertThat(lines.length).isGreaterThanOrEqualTo(30);

        // Verify some bulk records are present
        assertThat(csvContent).contains("BulkPub 000");
        assertThat(csvContent).contains("BulkPub 025");
    }
}
