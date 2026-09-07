package com.example.pressdistribution;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the issue defaults endpoint and automatic price defaulting behavior.
 * Covers the removal of the obsolete suggest-price POST endpoints and the new GET /issues/defaults endpoint.
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
class IssueDefaultsIntegrationTest {

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

    private Publication publicationWithIssues;
    private Publication publicationNoIssues;
    private Parish testParish;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user("admin@defaults-test.com").authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user("priest@defaults-test.com").authorities(new SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        testParish = new Parish();
        testParish.setLocality("Defaults Test Locality");
        testParish.setName("Defaults Test Parish");
        testParish = parishRepository.save(testParish);

        User admin = new User();
        admin.setFullName("Defaults Admin");
        admin.setEmail("admin@defaults-test.com");
        admin.setPasswordHash(passwordEncoder.encode("password"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("ABCDEFGHIJKLMNOP"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        User priest = new User();
        priest.setFullName("Defaults Priest");
        priest.setEmail("priest@defaults-test.com");
        priest.setPasswordHash(passwordEncoder.encode("password"));
        priest.setRecoveryCodeHash(passwordEncoder.encode("QRSTUVWXYZABCDEF"));
        priest.setRole(UserRole.PARISH_PRIEST);
        priest.setParish(testParish);
        priest.setActive(true);
        userRepository.save(priest);

        publicationWithIssues = new Publication();
        publicationWithIssues.setName("Defaults Daily News");
        publicationWithIssues = publicationRepository.save(publicationWithIssues);

        publicationNoIssues = new Publication();
        publicationNoIssues.setName("Defaults Empty Publication");
        publicationNoIssues = publicationRepository.save(publicationNoIssues);

        // Older issue
        Issue olderIssue = new Issue();
        olderIssue.setPublication(publicationWithIssues);
        olderIssue.setIssueNumber("DEF/01");
        olderIssue.setPublicationDate(LocalDate.of(2024, 1, 15));
        olderIssue.setUnitPrice(new BigDecimal("3.50"));
        olderIssue.setCreatedAt(LocalDateTime.of(2024, 1, 10, 10, 0, 0));
        olderIssue.setUpdatedAt(LocalDateTime.of(2024, 1, 10, 10, 0, 0));
        issueRepository.save(olderIssue);

        // Newer issue (most recent by createdAt)
        Issue newerIssue = new Issue();
        newerIssue.setPublication(publicationWithIssues);
        newerIssue.setIssueNumber("DEF/02");
        newerIssue.setPublicationDate(LocalDate.of(2024, 2, 15));
        newerIssue.setUnitPrice(new BigDecimal("4.00"));
        newerIssue.setCreatedAt(LocalDateTime.of(2024, 2, 10, 10, 0, 0));
        newerIssue.setUpdatedAt(LocalDateTime.of(2024, 2, 10, 10, 0, 0));
        issueRepository.save(newerIssue);
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        issueRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
        publicationRepository.deleteAll();
    }

    // ========== Obsolete suggest-price endpoints are gone ==========

    @Test
    @DisplayName("POST /admin/issues/suggest-price no longer exists")
    void adminSuggestPriceEndpointRemoved() throws Exception {
        // The endpoint is removed; any non-success response confirms it's gone
        mockMvc.perform(post("/admin/issues/suggest-price")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", publicationWithIssues.getId().toString()))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /issues/suggest-price no longer exists")
    void sharedSuggestPriceEndpointRemoved() throws Exception {
        // The endpoint is removed; any non-success response confirms it's gone
        mockMvc.perform(post("/issues/suggest-price")
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", publicationWithIssues.getId().toString()))
            .andExpect(status().is4xxClientError());
    }

    // ========== GET /issues/defaults endpoint ==========

    @Test
    @DisplayName("GET /issues/defaults returns unit price for publication with issues")
    void issueDefaultsReturnsPrice() throws Exception {
        mockMvc.perform(get("/issues/defaults")
                .with(ADMIN_USER)
                .param("publicationId", publicationWithIssues.getId().toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unitPrice").value(4.00));
    }

    @Test
    @DisplayName("GET /issues/defaults returns null price for publication with no issues")
    void issueDefaultsReturnsNullForEmptyPublication() throws Exception {
        mockMvc.perform(get("/issues/defaults")
                .with(ADMIN_USER)
                .param("publicationId", publicationNoIssues.getId().toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unitPrice").doesNotExist());
    }

    @Test
    @DisplayName("GET /issues/defaults returns 400 for non-existent publication")
    void issueDefaultsRejectsMissingPublication() throws Exception {
        mockMvc.perform(get("/issues/defaults")
                .with(ADMIN_USER)
                .param("publicationId", "999999")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /issues/defaults accessible by Parish Priest")
    void issueDefaultsAccessibleByPriest() throws Exception {
        mockMvc.perform(get("/issues/defaults")
                .with(PRIEST_USER)
                .param("publicationId", publicationWithIssues.getId().toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unitPrice").value(4.00));
    }

    @Test
    @DisplayName("GET /issues/defaults denied to unauthenticated users")
    void issueDefaultsDeniedToUnauthenticated() throws Exception {
        mockMvc.perform(get("/issues/defaults")
                .param("publicationId", publicationWithIssues.getId().toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /issues/defaults uses created_at DESC, id DESC ordering")
    void issueDefaultsUsesCorrectOrdering() throws Exception {
        // Create two issues with the same createdAt to test id DESC tie-breaking
        Publication tiePub = new Publication();
        tiePub.setName("Tie Defaults Publication");
        tiePub = publicationRepository.save(tiePub);

        LocalDateTime sameTimestamp = LocalDateTime.of(2024, 6, 1, 12, 0, 0, 0);

        Issue firstIssue = new Issue();
        firstIssue.setPublication(tiePub);
        firstIssue.setIssueNumber("TIEDEF/01");
        firstIssue.setPublicationDate(LocalDate.of(2024, 6, 1));
        firstIssue.setUnitPrice(new BigDecimal("5.00"));
        firstIssue.setCreatedAt(sameTimestamp);
        firstIssue.setUpdatedAt(sameTimestamp);
        firstIssue = issueRepository.save(firstIssue);

        Issue secondIssue = new Issue();
        secondIssue.setPublication(tiePub);
        secondIssue.setIssueNumber("TIEDEF/02");
        secondIssue.setPublicationDate(LocalDate.of(2024, 6, 2));
        secondIssue.setUnitPrice(new BigDecimal("7.50"));
        secondIssue.setCreatedAt(sameTimestamp);
        secondIssue.setUpdatedAt(sameTimestamp);
        secondIssue = issueRepository.save(secondIssue);

        assertThat(secondIssue.getId()).isGreaterThan(firstIssue.getId());

        mockMvc.perform(get("/issues/defaults")
                .with(ADMIN_USER)
                .param("publicationId", tiePub.getId().toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unitPrice").value(7.50));
    }

    @Test
    @DisplayName("GET /issues/defaults produces no side effects")
    void issueDefaultsNoSideEffects() throws Exception {
        long issueCountBefore = issueRepository.count();
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(get("/issues/defaults")
                .with(ADMIN_USER)
                .param("publicationId", publicationWithIssues.getId().toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        assertThat(issueRepository.count()).isEqualTo(issueCountBefore);
        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
    }

    // ========== Initial form load with preselected publication ==========

    @Test
    @DisplayName("Admin create issue form with publicationId preselects publication and fills price")
    void adminCreateIssueFormPreselected() throws Exception {
        mockMvc.perform(get("/admin/issues/new")
                .with(ADMIN_USER)
                .param("publicationId", publicationWithIssues.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("issueForm", hasProperty("publicationId", equalTo(publicationWithIssues.getId()))))
            .andExpect(model().attribute("issueForm", hasProperty("unitPrice", comparesEqualTo(new BigDecimal("4.00")))))
            .andExpect(model().attribute("issueForm", hasProperty("publicationDate", equalTo(LocalDate.now()))));
    }

    @Test
    @DisplayName("Priest create issue form with publicationId preselects publication and fills price")
    void priestCreateIssueFormPreselected() throws Exception {
        mockMvc.perform(get("/issues/new")
                .with(PRIEST_USER)
                .param("publicationId", publicationWithIssues.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("issueForm", hasProperty("publicationId", equalTo(publicationWithIssues.getId()))))
            .andExpect(model().attribute("issueForm", hasProperty("unitPrice", comparesEqualTo(new BigDecimal("4.00")))))
            .andExpect(model().attribute("issueForm", hasProperty("publicationDate", equalTo(LocalDate.now()))));
    }

    @Test
    @DisplayName("Create issue form defaults to today's date without publicationId")
    void createIssueFormDefaultsToToday() throws Exception {
        mockMvc.perform(get("/admin/issues/new").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(model().attribute("issueForm", hasProperty("publicationDate", equalTo(LocalDate.now()))))
            .andExpect(model().attribute("issueForm", hasProperty("unitPrice", nullValue())));
    }

    @Test
    @DisplayName("Create issue form with no previous issues leaves price empty")
    void createIssueFormNoPriorIssues() throws Exception {
        mockMvc.perform(get("/admin/issues/new")
                .with(ADMIN_USER)
                .param("publicationId", publicationNoIssues.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("issueForm", hasProperty("publicationId", equalTo(publicationNoIssues.getId()))))
            .andExpect(model().attribute("issueForm", hasProperty("unitPrice", nullValue())));
    }

    @Test
    @DisplayName("Create issue form with invalid publicationId shows error")
    void createIssueFormInvalidPublicationId() throws Exception {
        mockMvc.perform(get("/admin/issues/new")
                .with(ADMIN_USER)
                .param("publicationId", "999999"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("errorMessage"))
            .andExpect(model().attribute("issueForm", hasProperty("unitPrice", nullValue())));
    }

    // ========== Existing issue creation rules remain intact ==========

    @Test
    @DisplayName("Admin can still create issues normally")
    void adminCanCreateIssue() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", publicationWithIssues.getId().toString())
                .param("issueNumber", "NEW/01")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "6.50"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/issues"));
    }

    @Test
    @DisplayName("Priest can still create issues normally via shared endpoint")
    void priestCanCreateIssue() throws Exception {
        mockMvc.perform(post("/issues")
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", publicationWithIssues.getId().toString())
                .param("issueNumber", "PRIEST/01")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "6.50"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Unit price validation still requires price > 0")
    void unitPriceValidationStillActive() throws Exception {
        mockMvc.perform(post("/admin/issues")
                .with(ADMIN_USER)
                .with(csrf())
                .param("publicationId", publicationWithIssues.getId().toString())
                .param("issueNumber", "VAL/01")
                .param("publicationDate", "2024-06-01")
                .param("unitPrice", "0"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("issueForm", "unitPrice"));
    }

    // ========== Template no longer references suggest-price ==========

    @Test
    @DisplayName("Admin issue form does not contain suggest-price reference")
    void adminFormNoSuggestPriceReference() throws Exception {
        mockMvc.perform(get("/admin/issues/new").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("suggest-price"))))
            .andExpect(content().string(not(containsString("Suggest price"))));
    }

    @Test
    @DisplayName("Shared issue form does not contain suggest-price reference")
    void sharedFormNoSuggestPriceReference() throws Exception {
        mockMvc.perform(get("/issues/new").with(PRIEST_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("suggest-price"))))
            .andExpect(content().string(not(containsString("Suggest price"))));
    }
}
