package com.example.pressdistribution;

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
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class UxImprovementsIntegrationTest {

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

    private Parish parish;
    private Publication publication1;
    private Publication publication2;
    private Publication publicationNoIssues;
    private Issue issue1;
    private Issue issue2;
    private Issue issue3;

    private static final String ADMIN_EMAIL = "admin@improvements.com";
    private static final String PRIEST_EMAIL = "priest@improvements.com";

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user(ADMIN_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user(PRIEST_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        parish = new Parish();
        parish.setLocality("Test Locality");
        parish.setName("Test Parish");
        parish = parishRepository.save(parish);

        publication1 = new Publication();
        publication1.setName("Alpha Publication");
        publication1 = publicationRepository.save(publication1);

        publication2 = new Publication();
        publication2.setName("Beta Publication");
        publication2 = publicationRepository.save(publication2);

        publicationNoIssues = new Publication();
        publicationNoIssues.setName("Gamma Publication");
        publicationNoIssues = publicationRepository.save(publicationNoIssues);

        issue1 = new Issue();
        issue1.setPublication(publication1);
        issue1.setIssueNumber("A/01");
        issue1.setPublicationDate(LocalDate.of(2024, 1, 15));
        issue1.setUnitPrice(new BigDecimal("3.50"));
        issue1.setCreatedAt(LocalDateTime.of(2024, 1, 10, 10, 0, 0));
        issue1.setUpdatedAt(LocalDateTime.of(2024, 1, 10, 10, 0, 0));
        issue1 = issueRepository.save(issue1);

        issue2 = new Issue();
        issue2.setPublication(publication1);
        issue2.setIssueNumber("A/02");
        issue2.setPublicationDate(LocalDate.of(2024, 3, 20));
        issue2.setUnitPrice(new BigDecimal("4.50"));
        issue2.setCreatedAt(LocalDateTime.of(2024, 3, 10, 10, 0, 0));
        issue2.setUpdatedAt(LocalDateTime.of(2024, 3, 10, 10, 0, 0));
        issue2 = issueRepository.save(issue2);

        issue3 = new Issue();
        issue3.setPublication(publication2);
        issue3.setIssueNumber("B/01");
        issue3.setPublicationDate(LocalDate.of(2024, 2, 10));
        issue3.setUnitPrice(new BigDecimal("5.00"));
        issue3.setCreatedAt(LocalDateTime.of(2024, 2, 5, 10, 0, 0));
        issue3.setUpdatedAt(LocalDateTime.of(2024, 2, 5, 10, 0, 0));
        issue3 = issueRepository.save(issue3);

        User admin = new User();
        admin.setFullName("Test Admin");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode("password123"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("ABCDEFGHIJKLMNOP"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        User priest = new User();
        priest.setFullName("Test Priest");
        priest.setEmail(PRIEST_EMAIL);
        priest.setPasswordHash(passwordEncoder.encode("password123"));
        priest.setRecoveryCodeHash(passwordEncoder.encode("QRSTUVWXYZABCDEF"));
        priest.setRole(UserRole.PARISH_PRIEST);
        priest.setParish(parish);
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

    // ========== 1. Default Publication date in Create Issue ==========

    @Nested
    @DisplayName("1. Default Publication date in Create Issue")
    class DefaultPublicationDate {

        @Test
        @DisplayName("Admin create issue form has today's date by default")
        void adminCreateIssueFormHasTodayDate() throws Exception {
            mockMvc.perform(get("/admin/issues/new").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(model().attribute("issueForm",
                    org.hamcrest.Matchers.hasProperty("publicationDate",
                        org.hamcrest.Matchers.equalTo(LocalDate.now()))));
        }

        @Test
        @DisplayName("Shared create issue form has today's date by default")
        void sharedCreateIssueFormHasTodayDate() throws Exception {
            mockMvc.perform(get("/issues/new").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(model().attribute("issueForm",
                    org.hamcrest.Matchers.hasProperty("publicationDate",
                        org.hamcrest.Matchers.equalTo(LocalDate.now()))));
        }

        @Test
        @DisplayName("User-supplied date survives validation error on admin form")
        void adminFormPreservesUserDateOnValidationError() throws Exception {
            mockMvc.perform(post("/admin/issues")
                    .with(ADMIN_USER)
                    .with(csrf())
                    .param("publicationId", publication1.getId().toString())
                    .param("issueNumber", "")  // blank triggers validation error
                    .param("publicationDate", "2025-06-15")
                    .param("unitPrice", "3.00"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("issueForm",
                    org.hamcrest.Matchers.hasProperty("publicationDate",
                        org.hamcrest.Matchers.equalTo(LocalDate.of(2025, 6, 15)))));
        }

        @Test
        @DisplayName("User-supplied date survives validation error on shared form")
        void sharedFormPreservesUserDateOnValidationError() throws Exception {
            mockMvc.perform(post("/issues")
                    .with(PRIEST_USER)
                    .with(csrf())
                    .param("publicationId", publication1.getId().toString())
                    .param("issueNumber", "")  // blank triggers validation error
                    .param("publicationDate", "2025-06-15")
                    .param("unitPrice", "3.00"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("issueForm",
                    org.hamcrest.Matchers.hasProperty("publicationDate",
                        org.hamcrest.Matchers.equalTo(LocalDate.of(2025, 6, 15)))));
        }
    }

    // ========== 2. Filter Issues by Publication ==========

    @Nested
    @DisplayName("2. Filter Issues by Publication")
    class FilterIssuesByPublication {

        @Test
        @DisplayName("All publications view shows all issues")
        void allPublicationsShowsAllIssues() throws Exception {
            mockMvc.perform(get("/admin/issues").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A/01")))
                .andExpect(content().string(containsString("A/02")))
                .andExpect(content().string(containsString("B/01")));
        }

        @Test
        @DisplayName("Filtering by publication shows only matching issues")
        void filterByPublicationShowsOnlyMatching() throws Exception {
            mockMvc.perform(get("/admin/issues")
                    .with(ADMIN_USER)
                    .param("publicationId", publication1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A/01")))
                .andExpect(content().string(containsString("A/02")))
                .andExpect(content().string(not(containsString("B/01"))));
        }

        @Test
        @DisplayName("No results shows empty-state message")
        void noResultsShowsEmptyState() throws Exception {
            mockMvc.perform(get("/admin/issues")
                    .with(ADMIN_USER)
                    .param("publicationId", publicationNoIssues.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No issues found for the selected publication")));
        }

        @Test
        @DisplayName("Invalid publication ID shows error message")
        void invalidPublicationIdShowsError() throws Exception {
            mockMvc.perform(get("/admin/issues")
                    .with(ADMIN_USER)
                    .param("publicationId", "99999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The selected publication does not exist")));
        }

        @Test
        @DisplayName("Filter preserved in dropdown selection")
        void filterPreservedInDropdown() throws Exception {
            mockMvc.perform(get("/admin/issues")
                    .with(ADMIN_USER)
                    .param("publicationId", publication1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedPublicationId", publication1.getId()));
        }

        @Test
        @DisplayName("Shared issues list accessible by Parish Priest")
        void sharedIssuesListAccessibleByPriest() throws Exception {
            mockMvc.perform(get("/issues").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A/01")))
                .andExpect(content().string(containsString("B/01")));
        }

        @Test
        @DisplayName("Shared issues list filter by publication for Priest")
        void sharedIssuesListFilterByPublicationPriest() throws Exception {
            mockMvc.perform(get("/issues")
                    .with(PRIEST_USER)
                    .param("publicationId", publication2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("B/01")))
                .andExpect(content().string(not(containsString("A/01"))));
        }

        @Test
        @DisplayName("Admin issues list not accessible by Parish Priest")
        void adminIssuesListNotAccessibleByPriest() throws Exception {
            mockMvc.perform(get("/admin/issues").with(PRIEST_USER))
                .andExpect(status().isForbidden());
        }
    }

    // ========== 3. Latest Issue date on Publications page ==========

    @Nested
    @DisplayName("3. Latest Issue date on Publications page")
    class LatestIssueDateOnPublications {

        @Test
        @DisplayName("Publication with no issues shows dash")
        void publicationWithNoIssuesShowsDash() throws Exception {
            mockMvc.perform(get("/admin/publications").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gamma Publication")))
                .andExpect(content().string(containsString("\u2014")));
        }

        @Test
        @DisplayName("Publication with one issue shows its date")
        void publicationWithOneIssueShowsItsDate() throws Exception {
            mockMvc.perform(get("/admin/publications").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Beta Publication")))
                .andExpect(content().string(containsString("2024-02-10")));
        }

        @Test
        @DisplayName("Publication with multiple issues shows newest issue date")
        void publicationWithMultipleIssuesShowsNewestDate() throws Exception {
            // publication1 has issue1 (2024-01-15, createdAt 2024-01-10) and issue2 (2024-03-20, createdAt 2024-03-10)
            // Newest is issue2 (by created_at DESC), its publicationDate is 2024-03-20
            mockMvc.perform(get("/admin/publications").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alpha Publication")))
                .andExpect(content().string(containsString("2024-03-20")));
        }

        @Test
        @DisplayName("Tie-breaking uses created_at DESC then id DESC")
        void tieBreakingUsesCreatedAtDescIdDesc() throws Exception {
            // Create two issues with same created_at but different ids
            Publication tiePub = new Publication();
            tiePub.setName("Tie Publication");
            tiePub = publicationRepository.save(tiePub);

            Issue issueOlder = new Issue();
            issueOlder.setPublication(tiePub);
            issueOlder.setIssueNumber("T/01");
            issueOlder.setPublicationDate(LocalDate.of(2024, 5, 1));
            issueOlder.setUnitPrice(new BigDecimal("1.00"));
            issueOlder.setCreatedAt(LocalDateTime.of(2024, 6, 1, 10, 0, 0));
            issueOlder.setUpdatedAt(LocalDateTime.of(2024, 6, 1, 10, 0, 0));
            issueOlder = issueRepository.save(issueOlder);

            Issue issueNewer = new Issue();
            issueNewer.setPublication(tiePub);
            issueNewer.setIssueNumber("T/02");
            issueNewer.setPublicationDate(LocalDate.of(2024, 7, 1));
            issueNewer.setUnitPrice(new BigDecimal("2.00"));
            issueNewer.setCreatedAt(LocalDateTime.of(2024, 6, 1, 10, 0, 0)); // same createdAt
            issueNewer.setUpdatedAt(LocalDateTime.of(2024, 6, 1, 10, 0, 0));
            issueNewer = issueRepository.save(issueNewer);

            // issueNewer has higher id, so it wins the tie-break
            mockMvc.perform(get("/admin/publications").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2024-07-01")));
        }

        @Test
        @DisplayName("Multiple publications shown on same page with correct dates")
        void multiplePublicationsWithCorrectDates() throws Exception {
            mockMvc.perform(get("/admin/publications").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alpha Publication")))
                .andExpect(content().string(containsString("Beta Publication")))
                .andExpect(content().string(containsString("Gamma Publication")))
                .andExpect(content().string(containsString("Latest issue date")));
        }
    }

    // ========== 4. Create Record action from Issues list ==========

    @Nested
    @DisplayName("4. Create Record action from Issues list")
    class CreateRecordFromIssuesList {

        @Test
        @DisplayName("Admin sees Create record button for all issues")
        void adminSeesCreateRecordForAllIssues() throws Exception {
            mockMvc.perform(get("/admin/issues").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/records/new?issueId=" + issue1.getId())))
                .andExpect(content().string(containsString("/records/new?issueId=" + issue2.getId())))
                .andExpect(content().string(containsString("/records/new?issueId=" + issue3.getId())));
        }

        @Test
        @DisplayName("Admin navigates to record form with preselected issue")
        void adminNavigatesToRecordFormWithPreselectedIssue() throws Exception {
            mockMvc.perform(get("/records/new")
                    .with(ADMIN_USER)
                    .param("issueId", issue1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("recordForm",
                    org.hamcrest.Matchers.hasProperty("issueId",
                        org.hamcrest.Matchers.equalTo(issue1.getId()))));
        }

        @Test
        @DisplayName("Parish Priest sees Create record for issues without existing records")
        void priestSeesCreateRecordForEligibleIssues() throws Exception {
            mockMvc.perform(get("/issues").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/records/new?issueId=" + issue1.getId())))
                .andExpect(content().string(containsString("/records/new?issueId=" + issue2.getId())));
        }

        @Test
        @DisplayName("Parish Priest does not see Create record for issues with existing records")
        void priestDoesNotSeeCreateRecordForExistingRecords() throws Exception {
            createRecordForParish(parish, issue1, 10, 2, BigDecimal.ZERO);

            mockMvc.perform(get("/issues").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/records/new?issueId=" + issue1.getId()))))
                .andExpect(content().string(containsString("/records/new?issueId=" + issue2.getId())));
        }

        @Test
        @DisplayName("Invalid issue ID shows error message in record form")
        void invalidIssueIdShowsError() throws Exception {
            mockMvc.perform(get("/records/new")
                    .with(ADMIN_USER)
                    .param("issueId", "99999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("issueIdError", "The specified issue does not exist."));
        }

        @Test
        @DisplayName("Crafted duplicate request rejected for priest")
        void craftedDuplicateRequestRejected() throws Exception {
            createRecordForParish(parish, issue1, 10, 2, BigDecimal.ZERO);

            mockMvc.perform(post("/records/new")
                    .with(PRIEST_USER)
                    .with(csrf())
                    .param("issueId", issue1.getId().toString())
                    .param("deliveredCopies", "50")
                    .param("returnedCopies", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already exists")));
        }

        @Test
        @DisplayName("Admin issues list is not accessible by priest")
        void adminIssuesListNotAccessibleByPriest() throws Exception {
            mockMvc.perform(get("/admin/issues").with(PRIEST_USER))
                .andExpect(status().isForbidden());
        }
    }

    // ========== 5. Role-aware navigation links ==========

    @Nested
    @DisplayName("5. Role-aware navigation links in header")
    class NavigationLinks {

        @Test
        @DisplayName("Admin sees Publications, Parishes, Users, Parish report links")
        void adminSeesAllNavLinks() throws Exception {
            mockMvc.perform(get("/").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/publications")))
                .andExpect(content().string(containsString("/admin/parishes")))
                .andExpect(content().string(containsString("/admin/users")))
                .andExpect(content().string(containsString("/reports/parishes")));
        }

        @Test
        @DisplayName("Admin does not see Parish Priest-only links")
        void adminDoesNotSeePriestLinks() throws Exception {
            mockMvc.perform(get("/").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/reports/my-parish"))));
        }

        @Test
        @DisplayName("Parish Priest sees Parish report link")
        void priestSeesParishReportLink() throws Exception {
            mockMvc.perform(get("/").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/reports/my-parish")));
        }

        @Test
        @DisplayName("Parish Priest does not see admin links")
        void priestDoesNotSeeAdminLinks() throws Exception {
            mockMvc.perform(get("/").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/admin/publications"))))
                .andExpect(content().string(not(containsString("/admin/parishes"))))
                .andExpect(content().string(not(containsString("/admin/users"))));
        }

        @Test
        @DisplayName("Unauthenticated users do not see nav links")
        void unauthenticatedDoNotSeeNavLinks() throws Exception {
            mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/admin/publications"))))
                .andExpect(content().string(not(containsString("/admin/parishes"))))
                .andExpect(content().string(not(containsString("/admin/users"))));
        }

        @Test
        @DisplayName("Nav bar preserves profile and logout")
        void navBarPreservesProfileAndLogout() throws Exception {
            mockMvc.perform(get("/").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/profile")))
                .andExpect(content().string(containsString("Sign Out")));
        }
    }

    // ========== 6. Full-width Welcome screen buttons ==========

    @Nested
    @DisplayName("6. Full-width Welcome screen buttons")
    class FullWidthButtons {

        @Test
        @DisplayName("Admin home uses d-grid layout for full-width buttons")
        void adminHomeUsesGridLayout() throws Exception {
            mockMvc.perform(get("/").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("d-grid gap-2")));
        }

        @Test
        @DisplayName("Priest home uses d-grid layout for full-width buttons")
        void priestHomeUsesGridLayout() throws Exception {
            mockMvc.perform(get("/").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("d-grid gap-2")));
        }

        @Test
        @DisplayName("Home page does not use flex-wrap for buttons")
        void homePageDoesNotUseFlexWrap() throws Exception {
            mockMvc.perform(get("/").with(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("d-flex flex-wrap"))));
        }
    }

    // ========== 7. Application title on Login screen ==========

    @Nested
    @DisplayName("7. Application title on Login screen")
    class LoginTitle {

        @Test
        @DisplayName("Login page contains the application title")
        void loginPageContainsApplicationTitle() throws Exception {
            mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Press Distribution Management System")));
        }

        @Test
        @DisplayName("Login page uses h1 for the title")
        void loginPageUsesH1() throws Exception {
            mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1")));
        }

        @Test
        @DisplayName("Login page preserves forgot password link")
        void loginPagePreservesForgotPasswordLink() throws Exception {
            mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Forgot password?")))
                .andExpect(content().string(containsString("/login/recovery")));
        }

        @Test
        @DisplayName("Login page preserves error message display")
        void loginPagePreservesErrorMessage() throws Exception {
            mockMvc.perform(get("/login?error"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invalid email or password")));
        }
    }

    // ========== Helper methods ==========

    private ParishIssueRecord createRecordForParish(Parish parish, Issue issue, int delivered, int returned, BigDecimal paid) {
        ParishIssueRecord record = new ParishIssueRecord();
        record.setParish(parish);
        record.setIssue(issue);
        record.setDeliveredCopies(delivered);
        record.setReturnedCopies(returned);
        record.setPaidAmount(paid);
        return recordRepository.save(record);
    }
}
