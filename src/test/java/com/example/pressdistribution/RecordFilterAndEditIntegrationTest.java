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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for:
 * - Parish Issue Record edit fix (LazyInitializationException regression)
 * - Publication and Issue filter on records list
 * - Welcome page action order and visibility
 */
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
class RecordFilterAndEditIntegrationTest {

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

    private Parish parish1;
    private Parish parish2;
    private Publication publication1;
    private Publication publication2;
    private Issue issue1pub1;
    private Issue issue2pub1;
    private Issue issue1pub2;

    private static final String ADMIN_EMAIL = "admin@filter-edit-test.com";
    private static final String PRIEST_EMAIL = "priest@filter-edit-test.com";

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user(ADMIN_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user(PRIEST_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        parish1 = new Parish();
        parish1.setLocality("Locality One");
        parish1.setName("Parish Alpha");
        parish1 = parishRepository.save(parish1);

        parish2 = new Parish();
        parish2.setLocality("Locality Two");
        parish2.setName("Parish Beta");
        parish2 = parishRepository.save(parish2);

        publication1 = new Publication();
        publication1.setName("Weekly News");
        publication1 = publicationRepository.save(publication1);

        publication2 = new Publication();
        publication2.setName("Monthly Digest");
        publication2 = publicationRepository.save(publication2);

        issue1pub1 = new Issue();
        issue1pub1.setPublication(publication1);
        issue1pub1.setIssueNumber("2024/01");
        issue1pub1.setPublicationDate(LocalDate.of(2024, 1, 15));
        issue1pub1.setUnitPrice(new BigDecimal("2.50"));
        issue1pub1 = issueRepository.save(issue1pub1);

        issue2pub1 = new Issue();
        issue2pub1.setPublication(publication1);
        issue2pub1.setIssueNumber("2024/02");
        issue2pub1.setPublicationDate(LocalDate.of(2024, 2, 15));
        issue2pub1.setUnitPrice(new BigDecimal("2.50"));
        issue2pub1 = issueRepository.save(issue2pub1);

        issue1pub2 = new Issue();
        issue1pub2.setPublication(publication2);
        issue1pub2.setIssueNumber("2024/03");
        issue1pub2.setPublicationDate(LocalDate.of(2024, 3, 1));
        issue1pub2.setUnitPrice(new BigDecimal("4.00"));
        issue1pub2 = issueRepository.save(issue1pub2);

        User admin = new User();
        admin.setFullName("Admin User");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode("password123"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("ABCD-EFGH-IJKL-MNOP"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        User priest = new User();
        priest.setFullName("Priest User");
        priest.setEmail(PRIEST_EMAIL);
        priest.setPasswordHash(passwordEncoder.encode("password123"));
        priest.setRecoveryCodeHash(passwordEncoder.encode("QRST-UVWX-YZAB-CDEF"));
        priest.setRole(UserRole.PARISH_PRIEST);
        priest.setParish(parish1);
        priest.setActive(true);
        userRepository.save(priest);
    }

    @AfterEach
    void tearDown() {
        recordRepository.deleteAll();
        issueRepository.deleteAll();
        publicationRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
    }

    // ========== 1. Edit Form Regression Tests ==========

    @Nested
    @DisplayName("Edit form regression (LazyInitializationException fix)")
    class EditFormRegression {

        @Test
        @DisplayName("Admin opens edit form successfully")
        void adminOpensEditFormSuccessfully() throws Exception {
            ParishIssueRecord record = createRecord(parish1, issue1pub1, 100, 20, new BigDecimal("50.00"));

            mockMvc.perform(get("/records/" + record.getId() + "/edit").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit Record")))
                .andExpect(content().string(containsString("deliveredCopies")))
                .andExpect(content().string(containsString("returnedCopies")))
                .andExpect(content().string(containsString("paidAmount")));
        }

        @Test
        @DisplayName("Admin saves edit form successfully")
        void adminSavesEditFormSuccessfully() throws Exception {
            ParishIssueRecord record = createRecord(parish1, issue1pub1, 100, 20, new BigDecimal("50.00"));

            mockMvc.perform(post("/records/" + record.getId() + "/edit")
                    .with(ADMIN_USER)
                    .with(csrf())
                    .param("parishId", parish1.getId().toString())
                    .param("issueId", issue1pub1.getId().toString())
                    .param("deliveredCopies", "150")
                    .param("returnedCopies", "30")
                    .param("paidAmount", "75.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/records"));

            ParishIssueRecord updated = recordRepository.findById(record.getId()).orElseThrow();
            assertThat(updated.getDeliveredCopies()).isEqualTo(150);
            assertThat(updated.getReturnedCopies()).isEqualTo(30);
            assertThat(updated.getPaidAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
        }

        @Test
        @DisplayName("Priest opens edit form for own parish record (regression: was LazyInitException)")
        void priestOpensEditFormForOwnParishRecord() throws Exception {
            ParishIssueRecord record = createRecord(parish1, issue1pub1, 80, 10, new BigDecimal("25.00"));

            mockMvc.perform(get("/records/" + record.getId() + "/edit").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit Record")))
                .andExpect(content().string(containsString("deliveredCopies")))
                .andExpect(content().string(containsString("returnedCopies")))
                // Priest must NOT see paid amount
                .andExpect(content().string(not(containsString("paidAmount"))));
        }

        @Test
        @DisplayName("Priest saves edit form for own parish record")
        void priestSavesEditFormForOwnParishRecord() throws Exception {
            ParishIssueRecord record = createRecord(parish1, issue1pub1, 80, 10, new BigDecimal("25.00"));

            mockMvc.perform(post("/records/" + record.getId() + "/edit")
                    .with(PRIEST_USER)
                    .with(csrf())
                    .param("deliveredCopies", "90")
                    .param("returnedCopies", "15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/records"));

            ParishIssueRecord updated = recordRepository.findById(record.getId()).orElseThrow();
            assertThat(updated.getDeliveredCopies()).isEqualTo(90);
            assertThat(updated.getReturnedCopies()).isEqualTo(15);
            // Paid amount unchanged
            assertThat(updated.getPaidAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
        }

        @Test
        @DisplayName("Priest cannot edit another parish's record")
        void priestCannotEditAnotherParishRecord() throws Exception {
            ParishIssueRecord record = createRecord(parish2, issue1pub2, 60, 5, new BigDecimal("10.00"));

            mockMvc.perform(get("/records/" + record.getId() + "/edit").with(PRIEST_USER))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Priest cannot submit paid amount through crafted request")
        void priestCannotSubmitPaidAmountThroughCraftedRequest() throws Exception {
            ParishIssueRecord record = createRecord(parish1, issue1pub1, 80, 10, new BigDecimal("25.00"));

            mockMvc.perform(post("/records/" + record.getId() + "/edit")
                    .with(PRIEST_USER)
                    .with(csrf())
                    .param("deliveredCopies", "90")
                    .param("returnedCopies", "15")
                    .param("paidAmount", "999.99"))
                .andExpect(status().is3xxRedirection());

            ParishIssueRecord updated = recordRepository.findById(record.getId()).orElseThrow();
            assertThat(updated.getPaidAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
        }

        @Test
        @DisplayName("Invalid record ID returns 404")
        void invalidRecordIdReturns404() throws Exception {
            mockMvc.perform(get("/records/99999/edit").with(ADMIN_USER))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Returned copies exceeding delivered is rejected on edit")
        void returnedExceedsDeliveredRejectedOnEdit() throws Exception {
            ParishIssueRecord record = createRecord(parish1, issue1pub1, 80, 10, new BigDecimal("25.00"));

            mockMvc.perform(post("/records/" + record.getId() + "/edit")
                    .with(ADMIN_USER)
                    .with(csrf())
                    .param("parishId", parish1.getId().toString())
                    .param("issueId", issue1pub1.getId().toString())
                    .param("deliveredCopies", "10")
                    .param("returnedCopies", "20")
                    .param("paidAmount", "5.00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("must not exceed")));
        }

        @Test
        @DisplayName("Audit log created for successful update")
        void auditLogCreatedForSuccessfulUpdate() throws Exception {
            ParishIssueRecord record = createRecord(parish1, issue1pub1, 80, 10, new BigDecimal("25.00"));
            long auditCountBefore = auditLogRepository.count();

            mockMvc.perform(post("/records/" + record.getId() + "/edit")
                    .with(ADMIN_USER)
                    .with(csrf())
                    .param("parishId", parish1.getId().toString())
                    .param("issueId", issue1pub1.getId().toString())
                    .param("deliveredCopies", "100")
                    .param("returnedCopies", "20")
                    .param("paidAmount", "50.00"))
                .andExpect(status().is3xxRedirection());

            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs.size()).isGreaterThan((int) auditCountBefore);
            assertThat(logs).anyMatch(log ->
                log.getMessage().contains("updated") &&
                log.getMessage().contains("Locality One") &&
                log.getMessage().contains("Parish Alpha"));
        }
    }

    // ========== 2. Publication and Issue Filter Tests ==========

    @Nested
    @DisplayName("Records filtered by Publication and Issue")
    class RecordFilters {

        @Test
        @DisplayName("Filter by publication shows only records for that publication")
        void filterByPublicationShowsOnlyMatchingRecords() throws Exception {
            createRecord(parish1, issue1pub1, 100, 10, new BigDecimal("20.00"));
            createRecord(parish1, issue1pub2, 50, 5, new BigDecimal("10.00"));

            String html = mockMvc.perform(get("/records")
                    .with(ADMIN_USER)
                    .param("publicationId", publication1.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

            // Table body should contain Weekly News but not Monthly Digest
            String tableBody = html.substring(html.indexOf("<tbody>"), html.indexOf("</tbody>"));
            assertThat(tableBody).contains("Weekly News");
            assertThat(tableBody).doesNotContain("Monthly Digest");
        }

        @Test
        @DisplayName("Filter by publication and issue shows only that specific issue's records")
        void filterByPublicationAndIssue() throws Exception {
            createRecord(parish1, issue1pub1, 100, 10, new BigDecimal("20.00"));
            createRecord(parish1, issue2pub1, 80, 5, new BigDecimal("15.00"));
            createRecord(parish1, issue1pub2, 50, 5, new BigDecimal("10.00"));

            String html = mockMvc.perform(get("/records")
                    .with(ADMIN_USER)
                    .param("publicationId", publication1.getId().toString())
                    .param("issueId", issue1pub1.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

            // Table body should contain only issue1pub1's issue number
            String tableBody = html.substring(html.indexOf("<tbody>"), html.indexOf("</tbody>"));
            assertThat(tableBody).contains("2024/01");
            assertThat(tableBody).doesNotContain("2024/02");
            assertThat(tableBody).doesNotContain("Monthly Digest");
        }

        @Test
        @DisplayName("Issue dropdown contains only issues belonging to selected publication")
        void issueDropdownContainsOnlySelectedPublicationIssues() throws Exception {
            mockMvc.perform(get("/records")
                    .with(ADMIN_USER)
                    .param("publicationId", publication1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2024/01")))
                .andExpect(content().string(containsString("2024/02")))
                .andExpect(content().string(not(containsString("2024/03"))));
        }

        @Test
        @DisplayName("Invalid issue + publication combination resets issue filter")
        void invalidIssuePublicationCombinationResetsIssueFilter() throws Exception {
            createRecord(parish1, issue1pub1, 100, 10, new BigDecimal("20.00"));
            createRecord(parish1, issue1pub2, 50, 5, new BigDecimal("10.00"));

            // issue1pub2 belongs to publication2, not publication1
            mockMvc.perform(get("/records")
                    .with(ADMIN_USER)
                    .param("publicationId", publication1.getId().toString())
                    .param("issueId", issue1pub2.getId().toString()))
                .andExpect(status().isOk())
                // Should show all records for publication1 since issueId was invalid for this publication
                .andExpect(content().string(containsString("Weekly News")));
        }

        @Test
        @DisplayName("Pagination preserves publication and issue filters")
        void paginationPreservesFilters() throws Exception {
            createRecord(parish1, issue1pub1, 100, 10, new BigDecimal("20.00"));

            mockMvc.perform(get("/records")
                    .with(ADMIN_USER)
                    .param("publicationId", publication1.getId().toString())
                    .param("issueId", issue1pub1.getId().toString())
                    .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Weekly News")));
        }

        @Test
        @DisplayName("Parish Priest sees only own parish records after filtering")
        void priestSeesOnlyOwnParishRecordsAfterFiltering() throws Exception {
            createRecord(parish1, issue1pub1, 100, 10, new BigDecimal("20.00"));
            createRecord(parish2, issue1pub1, 60, 5, new BigDecimal("15.00"));

            mockMvc.perform(get("/records")
                    .with(PRIEST_USER)
                    .param("publicationId", publication1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Weekly News")))
                // Priest should not see parish2's data
                .andExpect(content().string(not(containsString("Locality Two"))))
                .andExpect(content().string(not(containsString("Parish Beta"))));
        }

        @Test
        @DisplayName("No records matching filters shows empty state message")
        void noRecordsShowsEmptyState() throws Exception {
            // No records exist for publication2
            mockMvc.perform(get("/records")
                    .with(ADMIN_USER)
                    .param("publicationId", publication2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No Parish Issue Records match the selected filters")));
        }

        @Test
        @DisplayName("Invalid publication ID is handled safely")
        void invalidPublicationIdHandledSafely() throws Exception {
            createRecord(parish1, issue1pub1, 100, 10, new BigDecimal("20.00"));

            mockMvc.perform(get("/records")
                    .with(ADMIN_USER)
                    .param("publicationId", "99999"))
                .andExpect(status().isOk())
                // Should show all records since invalid publicationId is treated as null
                .andExpect(content().string(containsString("Weekly News")));
        }

        @Test
        @DisplayName("No filter shows all records")
        void noFilterShowsAllRecords() throws Exception {
            createRecord(parish1, issue1pub1, 100, 10, new BigDecimal("20.00"));
            createRecord(parish1, issue1pub2, 50, 5, new BigDecimal("10.00"));

            mockMvc.perform(get("/records").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Weekly News")))
                .andExpect(content().string(containsString("Monthly Digest")));
        }
    }

    // ========== 3. Welcome Page Tests ==========

    @Nested
    @DisplayName("Welcome page order and visibility")
    class WelcomePageTests {

        @Test
        @DisplayName("Welcome page displays Issues before Records for admin")
        void welcomePageIssuesBeforeRecordsAdmin() throws Exception {
            String html = mockMvc.perform(get("/").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

            int issuesPos = html.indexOf("/admin/issues");
            int recordsPos = html.indexOf("/records");
            assertThat(issuesPos).isLessThan(recordsPos);
        }

        @Test
        @DisplayName("Welcome page displays Issues before Records for priest")
        void welcomePageIssuesBeforeRecordsPriest() throws Exception {
            String html = mockMvc.perform(get("/").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

            int issuesPos = html.indexOf("/issues");
            int recordsPos = html.indexOf("/records");
            assertThat(issuesPos).isLessThan(recordsPos);
        }

        @Test
        @DisplayName("Parish Priest no longer sees Create Issue on welcome page")
        void priestDoesNotSeeCreateIssueOnWelcomePage() throws Exception {
            mockMvc.perform(get("/").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/issues/new"))))
                .andExpect(content().string(not(containsString("Create issue"))));
        }

        @Test
        @DisplayName("Parish Priest can still access issue creation from Issues page")
        void priestCanStillAccessIssueCreationFromIssuesPage() throws Exception {
            mockMvc.perform(get("/issues/new").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create issue")));
        }

        @Test
        @DisplayName("Admin still sees Issues action on welcome page")
        void adminStillSeesIssuesAction() throws Exception {
            mockMvc.perform(get("/").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/issues")));
        }

        @Test
        @DisplayName("Priest welcome page preserves existing actions")
        void priestWelcomePagePreservesExistingActions() throws Exception {
            mockMvc.perform(get("/").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/records")))
                .andExpect(content().string(containsString("/issues")))
                .andExpect(content().string(containsString("/reports/my-parish")));
        }
    }

    // ========== Helper Methods ==========

    private ParishIssueRecord createRecord(Parish parish, Issue issue, int delivered, int returned, BigDecimal paidAmount) {
        ParishIssueRecord record = new ParishIssueRecord();
        record.setParish(parish);
        record.setIssue(issue);
        record.setDeliveredCopies(delivered);
        record.setReturnedCopies(returned);
        record.setPaidAmount(paidAmount);
        return recordRepository.save(record);
    }
}
