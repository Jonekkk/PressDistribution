package com.example.pressdistribution;

import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.IssueRepository;
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
import org.springframework.test.web.servlet.MvcResult;
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
class PriestIssueCreationIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Publication testPublication;
    private Parish testParish;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user("admin@issue-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user("priest@issue-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        // Create test parish (needed for priest user)
        testParish = new Parish();
        testParish.setLocality("Test Locality");
        testParish.setName("Test Parish");
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

        // Create test publication
        testPublication = new Publication();
        testPublication.setName("Daily News");
        testPublication = publicationRepository.save(testPublication);
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        issueRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
        publicationRepository.deleteAll();
    }

    // --- Successful Issue Creation ---

    @Test
    void successfulIssueCreation_redirectsToRecordForm() throws Exception {
        long issueCountBefore = issueRepository.count();

        MvcResult result = mockMvc.perform(post("/issues")
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "2024/01")
                .param("publicationDate", "2024-01-15")
                .param("unitPrice", "3.50"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("successMessage",
                "Issue created successfully. You can now create a record for this issue."))
            .andReturn();

        // Verify issue was persisted
        assertThat(issueRepository.count()).isEqualTo(issueCountBefore + 1);

        // Verify redirect URL contains issueId
        String redirectUrl = result.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).startsWith("/records/new?issueId=");

        // Extract issueId and verify it references the created issue
        String issueIdStr = redirectUrl.replace("/records/new?issueId=", "");
        Long issueId = Long.parseLong(issueIdStr);
        assertThat(issueRepository.findById(issueId)).isPresent();

        // Verify AuditLog entry created
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("Issue created") &&
            log.getMessage().contains("Daily News") &&
            log.getMessage().contains("2024/01"));
    }

    // --- Duplicate Issue Number Rejection ---

    @Test
    void duplicateIssueNumber_showsFieldError() throws Exception {
        // Create an existing issue
        Issue existing = new Issue();
        existing.setPublication(testPublication);
        existing.setIssueNumber("2024/01");
        existing.setPublicationDate(LocalDate.of(2024, 1, 15));
        existing.setUnitPrice(new BigDecimal("3.50"));
        issueRepository.save(existing);

        long issueCountBefore = issueRepository.count();

        // Try to create another issue with the same number for the same publication
        mockMvc.perform(post("/issues")
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "2024/01")
                .param("publicationDate", "2024-02-15")
                .param("unitPrice", "4.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "An issue with this number already exists for the selected publication")));

        // No new issue created
        assertThat(issueRepository.count()).isEqualTo(issueCountBefore);
    }

    // --- Missing Publication Rejection ---

    @Test
    void missingPublication_showsFieldError() throws Exception {
        long issueCountBefore = issueRepository.count();

        mockMvc.perform(post("/issues")
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", "99999")
                .param("issueNumber", "2024/01")
                .param("publicationDate", "2024-01-15")
                .param("unitPrice", "3.50"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "Selected publication does not exist")));

        // No issue created
        assertThat(issueRepository.count()).isEqualTo(issueCountBefore);
    }

    // --- Invalid Issue Number Rejection ---

    @Test
    void invalidIssueNumber_showsFieldError() throws Exception {
        long issueCountBefore = issueRepository.count();

        mockMvc.perform(post("/issues")
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "Issue#1!")
                .param("publicationDate", "2024-01-15")
                .param("unitPrice", "3.50"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "Issue number may only contain letters, digits, spaces, slashes and hyphens")));

        // No issue created
        assertThat(issueRepository.count()).isEqualTo(issueCountBefore);
    }

    // --- Blank Required Fields Rejection ---

    @Test
    void blankRequiredFields_showsErrors() throws Exception {
        long issueCountBefore = issueRepository.count();

        mockMvc.perform(post("/issues")
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", "")
                .param("issueNumber", "")
                .param("publicationDate", "")
                .param("unitPrice", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Publication is required")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Issue number is required")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Publication date is required")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Unit price is required")));

        // No issue created
        assertThat(issueRepository.count()).isEqualTo(issueCountBefore);
    }

    // --- Negative Unit Price Rejection ---

    @Test
    void negativeUnitPrice_showsFieldError() throws Exception {
        long issueCountBefore = issueRepository.count();

        mockMvc.perform(post("/issues")
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "2024/01")
                .param("publicationDate", "2024-01-15")
                .param("unitPrice", "-1.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "Unit price must be greater than zero")));

        // No issue created
        assertThat(issueRepository.count()).isEqualTo(issueCountBefore);
    }

    // --- Security: CSRF missing ---

    @Test
    void missingCsrf_returns403() throws Exception {
        mockMvc.perform(post("/issues")
                .with(PRIEST_USER)
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "2024/01")
                .param("publicationDate", "2024-01-15")
                .param("unitPrice", "3.50"))
            .andExpect(status().isForbidden());
    }

    // --- Security: Unauthenticated access ---

    @Test
    void unauthenticated_getForm_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/issues/new"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void unauthenticated_postCreate_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/issues")
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "2024/01")
                .param("publicationDate", "2024-01-15")
                .param("unitPrice", "3.50"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    // --- Administrator can also create issues ---

    @Test
    void administratorCanCreateIssue() throws Exception {
        long issueCountBefore = issueRepository.count();

        MvcResult result = mockMvc.perform(post("/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "2024/02")
                .param("publicationDate", "2024-02-15")
                .param("unitPrice", "4.00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("successMessage",
                "Issue created successfully. You can now create a record for this issue."))
            .andReturn();

        // Verify issue was persisted
        assertThat(issueRepository.count()).isEqualTo(issueCountBefore + 1);

        // Verify redirect URL
        String redirectUrl = result.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).startsWith("/records/new?issueId=");
    }

    // --- Created issue visible globally ---

    @Test
    void createdIssueVisibleGlobally() throws Exception {
        // Priest creates an issue
        mockMvc.perform(post("/issues")
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "2024/03")
                .param("publicationDate", "2024-03-15")
                .param("unitPrice", "5.00"))
            .andExpect(status().is3xxRedirection());

        // Admin can see the issue in the admin issue list
        mockMvc.perform(get("/admin/issues")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("2024/03")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Daily News")));
    }
}
