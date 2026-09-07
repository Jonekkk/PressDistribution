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
class UxFixesIntegrationTest {

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
    private Publication publication;
    private Issue issue1;
    private Issue issue2;
    private Issue issue3;

    private static final String ADMIN_EMAIL = "admin@ux-test.com";
    private static final String PRIEST_EMAIL = "priest@ux-test.com";

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user(ADMIN_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user(PRIEST_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        parish = new Parish();
        parish.setLocality("UX Test Locality");
        parish.setName("UX Test Parish");
        parish = parishRepository.save(parish);

        publication = new Publication();
        publication.setName("UX Test Publication");
        publication = publicationRepository.save(publication);

        issue1 = new Issue();
        issue1.setPublication(publication);
        issue1.setIssueNumber("UX/01");
        issue1.setPublicationDate(LocalDate.of(2024, 1, 15));
        issue1.setUnitPrice(new BigDecimal("3.50"));
        issue1.setCreatedAt(LocalDateTime.of(2024, 1, 10, 10, 0, 0));
        issue1.setUpdatedAt(LocalDateTime.of(2024, 1, 10, 10, 0, 0));
        issue1 = issueRepository.save(issue1);

        issue2 = new Issue();
        issue2.setPublication(publication);
        issue2.setIssueNumber("UX/02");
        issue2.setPublicationDate(LocalDate.of(2024, 2, 15));
        issue2.setUnitPrice(new BigDecimal("4.50"));
        issue2.setCreatedAt(LocalDateTime.of(2024, 2, 10, 10, 0, 0));
        issue2.setUpdatedAt(LocalDateTime.of(2024, 2, 10, 10, 0, 0));
        issue2 = issueRepository.save(issue2);

        issue3 = new Issue();
        issue3.setPublication(publication);
        issue3.setIssueNumber("UX/03");
        issue3.setPublicationDate(LocalDate.of(2024, 3, 15));
        issue3.setUnitPrice(new BigDecimal("5.00"));
        issue3.setCreatedAt(LocalDateTime.of(2024, 3, 10, 10, 0, 0));
        issue3.setUpdatedAt(LocalDateTime.of(2024, 3, 10, 10, 0, 0));
        issue3 = issueRepository.save(issue3);

        User admin = new User();
        admin.setFullName("UX Admin");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode("password123"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("ABCDEFGHIJKLMNOP"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        User priest = new User();
        priest.setFullName("UX Priest");
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

    // ========== Issue 8: Login page recovery link ==========

    @Test
    @DisplayName("Login page contains 'Forgot password?' link pointing to /login/recovery")
    void loginPageHasForgotPasswordLink() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Forgot password?")))
            .andExpect(content().string(containsString("/login/recovery")));
    }

    @Test
    @DisplayName("Recovery page is accessible to unauthenticated users")
    void recoveryPageAccessibleToUnauthenticatedUsers() throws Exception {
        mockMvc.perform(get("/login/recovery"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Reset Password")));
    }

    // ========== Issue 2: Profile hides parish for administrators ==========

    @Test
    @DisplayName("Administrator profile view does not display Parish field")
    void adminProfileViewHidesParish() throws Exception {
        mockMvc.perform(get("/profile").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Administrator")))
            .andExpect(content().string(not(containsString("<dt class=\"col-sm-4\">Parish</dt>"))));
    }

    @Test
    @DisplayName("Parish Priest profile view displays Parish")
    void priestProfileViewShowsParish() throws Exception {
        mockMvc.perform(get("/profile").with(PRIEST_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<dt class=\"col-sm-4\">Parish</dt>")))
            .andExpect(content().string(containsString("UX Test Locality")));
    }

    @Test
    @DisplayName("Administrator profile edit does not display Parish field")
    void adminProfileEditHidesParish() throws Exception {
        mockMvc.perform(get("/profile/edit").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<dt class=\"col-sm-4\">Parish</dt>"))));
    }

    // ========== Issue 3: Create User form server validation warning ==========

    @Test
    @DisplayName("Create user form shows password re-entry warning on validation error")
    void createUserFormShowsPasswordWarningOnError() throws Exception {
        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "")  // Empty triggers validation error
                .param("email", "new@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "ValidPass1!"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("please enter the password again")));
    }

    // ========== Issue 4: Publications list has 'Add new issue' button ==========

    @Test
    @DisplayName("Publications list contains 'Add new issue' link for each publication")
    void publicationsListHasAddNewIssueButton() throws Exception {
        mockMvc.perform(get("/admin/publications").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Add new issue")))
            .andExpect(content().string(containsString("/admin/issues/new?publicationId=" + publication.getId())));
    }

    // ========== Issue 5: Default Unit price and Publication date ==========

    @Test
    @DisplayName("Create issue form with publicationId preselects publication and auto-fills price")
    void createIssueFormWithPublicationIdPreselected() throws Exception {
        mockMvc.perform(get("/admin/issues/new")
                .with(ADMIN_USER)
                .param("publicationId", publication.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("issueForm"))
            .andExpect(model().attribute("issueForm",
                org.hamcrest.Matchers.hasProperty("publicationId",
                    org.hamcrest.Matchers.equalTo(publication.getId()))))
            .andExpect(model().attribute("issueForm",
                org.hamcrest.Matchers.hasProperty("unitPrice",
                    org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("5.00")))))
            .andExpect(model().attribute("issueForm",
                org.hamcrest.Matchers.hasProperty("publicationDate",
                    org.hamcrest.Matchers.equalTo(LocalDate.now()))));
    }

    @Test
    @DisplayName("Create issue form without publicationId has today's date and no price")
    void createIssueFormWithoutPublicationIdHasTodayDateNoPrice() throws Exception {
        mockMvc.perform(get("/admin/issues/new").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(model().attribute("issueForm",
                org.hamcrest.Matchers.hasProperty("publicationDate",
                    org.hamcrest.Matchers.equalTo(LocalDate.now()))))
            .andExpect(model().attribute("issueForm",
                org.hamcrest.Matchers.hasProperty("unitPrice",
                    org.hamcrest.Matchers.nullValue())));
    }

    @Test
    @DisplayName("Create issue form with no previous issues for publication has no default price")
    void createIssueFormNoPreviousIssuesNoPriceDefault() throws Exception {
        Publication emptyPub = new Publication();
        emptyPub.setName("Empty UX Publication");
        emptyPub = publicationRepository.save(emptyPub);

        mockMvc.perform(get("/admin/issues/new")
                .with(ADMIN_USER)
                .param("publicationId", emptyPub.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("issueForm",
                org.hamcrest.Matchers.hasProperty("unitPrice",
                    org.hamcrest.Matchers.nullValue())));
    }

    @Test
    @DisplayName("Price suggestion uses created_at DESC, id DESC ordering")
    void priceSuggestionUsesCorrectOrdering() throws Exception {
        // issue3 has the latest createdAt, so its price (5.00) should be suggested
        mockMvc.perform(get("/admin/issues/new")
                .with(ADMIN_USER)
                .param("publicationId", publication.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("issueForm",
                org.hamcrest.Matchers.hasProperty("unitPrice",
                    org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("5.00")))));
    }

    @Test
    @DisplayName("Shared (priest) create issue form also defaults date and price")
    void sharedCreateIssueFormDefaultsDateAndPrice() throws Exception {
        mockMvc.perform(get("/issues/new")
                .with(PRIEST_USER)
                .param("publicationId", publication.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("issueForm",
                org.hamcrest.Matchers.hasProperty("publicationDate",
                    org.hamcrest.Matchers.equalTo(LocalDate.now()))))
            .andExpect(model().attribute("issueForm",
                org.hamcrest.Matchers.hasProperty("unitPrice",
                    org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("5.00")))));
    }

    // ========== Issue 6: Default values in Create Record ==========

    @Test
    @DisplayName("Admin create record form defaults returnedCopies=0 and paidAmount=0")
    void adminCreateRecordFormDefaults() throws Exception {
        mockMvc.perform(get("/records/new").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(model().attribute("recordForm",
                org.hamcrest.Matchers.hasProperty("returnedCopies",
                    org.hamcrest.Matchers.equalTo(0))))
            .andExpect(model().attribute("recordForm",
                org.hamcrest.Matchers.hasProperty("paidAmount",
                    org.hamcrest.Matchers.comparesEqualTo(BigDecimal.ZERO))));
    }

    @Test
    @DisplayName("Priest create record form defaults returnedCopies=0")
    void priestCreateRecordFormDefaults() throws Exception {
        mockMvc.perform(get("/records/new").with(PRIEST_USER))
            .andExpect(status().isOk())
            .andExpect(model().attribute("recordForm",
                org.hamcrest.Matchers.hasProperty("returnedCopies",
                    org.hamcrest.Matchers.equalTo(0))));
    }

    // ========== Issue 9: Parish Priest sees only issues without records ==========

    @Nested
    @DisplayName("Issue 9: Priest record creation shows only issues without existing records")
    class PriestRecordIssueFiltering {

        @Test
        @DisplayName("Priest with records for some issues sees only remaining issues")
        void priestSeesOnlyMissingIssues() throws Exception {
            // Create a record for issue1
            createRecordForParish(parish, issue1, 10, 2, BigDecimal.ZERO);

            mockMvc.perform(get("/records/new").with(PRIEST_USER))
                .andExpect(status().isOk())
                // Should not see issue1 in dropdown
                .andExpect(content().string(not(containsString("UX/01"))))
                // Should see issue2 and issue3
                .andExpect(content().string(containsString("UX/02")))
                .andExpect(content().string(containsString("UX/03")));
        }

        @Test
        @DisplayName("Priest with records for all issues sees 'no issues available' message")
        void priestSeesNoIssuesAvailableMessage() throws Exception {
            // Create records for all issues
            createRecordForParish(parish, issue1, 10, 2, BigDecimal.ZERO);
            createRecordForParish(parish, issue2, 20, 5, BigDecimal.ZERO);
            createRecordForParish(parish, issue3, 30, 8, BigDecimal.ZERO);

            mockMvc.perform(get("/records/new").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No issues are available for a new record")));
        }

        @Test
        @DisplayName("Duplicate parish+issue creation attempt is rejected")
        void duplicateCreationAttemptRejected() throws Exception {
            // Create a record for issue1
            createRecordForParish(parish, issue1, 10, 2, BigDecimal.ZERO);

            // Attempt to create another record for same parish+issue via crafted request
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
        @DisplayName("Administrator still sees all issues in create record form")
        void adminSeesAllIssuesInCreateRecordForm() throws Exception {
            // Create a record for issue1 using admin for this parish
            createRecordForParish(parish, issue1, 10, 2, new BigDecimal("5.00"));

            mockMvc.perform(get("/records/new").with(ADMIN_USER))
                .andExpect(status().isOk())
                // Admin should see all issues including issue1
                .andExpect(content().string(containsString("UX/01")))
                .andExpect(content().string(containsString("UX/02")))
                .andExpect(content().string(containsString("UX/03")));
        }

        @Test
        @DisplayName("Priest with no records sees all issues")
        void priestWithNoRecordsSeesAllIssues() throws Exception {
            mockMvc.perform(get("/records/new").with(PRIEST_USER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UX/01")))
                .andExpect(content().string(containsString("UX/02")))
                .andExpect(content().string(containsString("UX/03")));
        }
    }

    // ========== Issue 7: Parish hint in create user form ==========

    @Test
    @DisplayName("Create user form contains parish hint text")
    void createUserFormContainsParishHint() throws Exception {
        mockMvc.perform(get("/admin/users/new").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Required for the Parish Priest role.")));
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
