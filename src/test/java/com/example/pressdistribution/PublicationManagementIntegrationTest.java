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
class PublicationManagementIntegrationTest {

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
        user("admin@pub-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user("priest@pub-test.com").authorities(
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
        admin.setEmail("admin@pub-test.com");
        admin.setPasswordHash(passwordEncoder.encode("password"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("recovery"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        // Create parish priest user
        User priest = new User();
        priest.setFullName("Test Priest");
        priest.setEmail("priest@pub-test.com");
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

        // Create additional publications for sort testing
        Publication p1 = new Publication();
        p1.setName("Zebra Times");
        publicationRepository.save(p1);

        Publication p2 = new Publication();
        p2.setName("alpha news");
        publicationRepository.save(p2);

        Publication p3 = new Publication();
        p3.setName("Beta Weekly");
        publicationRepository.save(p3);
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        issueRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
        publicationRepository.deleteAll();
    }

    // --- Security: Unauthenticated access ---

    @Test
    void testUnauthenticatedGet_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/publications"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testUnauthenticatedPost_withCsrf_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/admin/publications")
                .with(csrf())
                .param("name", "Some Publication"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testUnauthenticatedPost_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/admin/publications")
                .param("name", "Some Publication"))
            .andExpect(status().isForbidden());
    }

    // --- Security: Parish Priest forbidden ---

    @Test
    void testParishPriest_getList_returns403() throws Exception {
        mockMvc.perform(get("/admin/publications")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_getNew_returns403() throws Exception {
        mockMvc.perform(get("/admin/publications/new")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_getEdit_returns403() throws Exception {
        mockMvc.perform(get("/admin/publications/" + testPublication.getId() + "/edit")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_postCreate_returns403() throws Exception {
        mockMvc.perform(post("/admin/publications")
                .with(PRIEST_USER)
                .with(csrf())
                .param("name", "New Publication"))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_postUpdate_returns403() throws Exception {
        mockMvc.perform(post("/admin/publications/" + testPublication.getId())
                .with(PRIEST_USER)
                .with(csrf())
                .param("name", "Hacked Name"))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_postDelete_returns403() throws Exception {
        mockMvc.perform(post("/admin/publications/" + testPublication.getId() + "/delete")
                .with(PRIEST_USER)
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    // --- Security: CSRF protection ---

    @Test
    void testMissingCsrf_returns403() throws Exception {
        mockMvc.perform(post("/admin/publications")
                .with(ADMIN_USER)
                .param("name", "New Publication"))
            .andExpect(status().isForbidden());
    }

    // --- List ---

    @Test
    void testAdminGetList_returns200WithTable() throws Exception {
        mockMvc.perform(get("/admin/publications")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Daily News")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Zebra Times")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<table")));
    }

    @Test
    void testListSorting_caseInsensitive() throws Exception {
        String html = mockMvc.perform(get("/admin/publications")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Expected order (case-insensitive by name):
        // "alpha news"
        // "Beta Weekly"
        // "Daily News"
        // "Zebra Times"
        int posAlpha = html.indexOf("alpha news");
        int posBeta = html.indexOf("Beta Weekly");
        int posDaily = html.indexOf("Daily News");
        int posZebra = html.indexOf("Zebra Times");

        assertThat(posAlpha).isLessThan(posBeta);
        assertThat(posBeta).isLessThan(posDaily);
        assertThat(posDaily).isLessThan(posZebra);
    }

    @Test
    void testNoPublications_emptyTableBody() throws Exception {
        // Delete all test data
        issueRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
        publicationRepository.deleteAll();

        mockMvc.perform(get("/admin/publications")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<thead>")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Name")));
    }

    // --- Create ---

    @Test
    void testAdminCreatePublication_success() throws Exception {
        long countBefore = publicationRepository.count();

        mockMvc.perform(post("/admin/publications")
                .with(ADMIN_USER)
                .with(csrf())
                .param("name", "New Publication"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/publications"))
            .andExpect(flash().attribute("successMessage", "Publication created successfully"));

        assertThat(publicationRepository.count()).isEqualTo(countBefore + 1);

        // Verify audit log entry
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("created") &&
            log.getMessage().contains("New Publication"));
    }

    @Test
    void testCreatePublication_blankName_rejected() throws Exception {
        mockMvc.perform(post("/admin/publications")
                .with(ADMIN_USER)
                .with(csrf())
                .param("name", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Publication name is required")));
    }

    @Test
    void testCreatePublication_nameTooLong_rejected() throws Exception {
        String longName = "a".repeat(256);
        mockMvc.perform(post("/admin/publications")
                .with(ADMIN_USER)
                .with(csrf())
                .param("name", longName))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Publication name must not exceed 255 characters")));
    }

    @Test
    void testCreatePublication_duplicateName_caseInsensitive_rejected() throws Exception {
        long countBefore = publicationRepository.count();

        mockMvc.perform(post("/admin/publications")
                .with(ADMIN_USER)
                .with(csrf())
                .param("name", "daily news"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("A publication with this name already exists")));

        assertThat(publicationRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void testCreatePublication_capitalizationPreserved() throws Exception {
        mockMvc.perform(post("/admin/publications")
                .with(ADMIN_USER)
                .with(csrf())
                .param("name", "The Evening Post"))
            .andExpect(status().is3xxRedirection());

        Publication created = publicationRepository.findAllSortedByName().stream()
            .filter(p -> p.getName().equals("The Evening Post"))
            .findFirst()
            .orElse(null);
        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo("The Evening Post");
    }

    // --- Edit ---

    @Test
    void testEditFormPrePopulated() throws Exception {
        mockMvc.perform(get("/admin/publications/" + testPublication.getId() + "/edit")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Daily News")));
    }

    @Test
    void testAdminUpdatePublication_success() throws Exception {
        mockMvc.perform(post("/admin/publications/" + testPublication.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("name", "Updated Daily"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/publications"))
            .andExpect(flash().attribute("successMessage", "Publication updated successfully"));

        Publication updated = publicationRepository.findById(testPublication.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Updated Daily");

        // Verify audit log entry
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("updated") &&
            log.getMessage().contains("Updated Daily"));
    }

    @Test
    void testUpdatePublication_duplicateRejection_excludesSelf() throws Exception {
        // Try to update testPublication to have same name as "Zebra Times"
        mockMvc.perform(post("/admin/publications/" + testPublication.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("name", "zebra times"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("A publication with this name already exists")));

        // Publication unchanged
        Publication unchanged = publicationRepository.findById(testPublication.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Daily News");
    }

    @Test
    void testUpdatePublication_sameNameAccepted() throws Exception {
        mockMvc.perform(post("/admin/publications/" + testPublication.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("name", "Daily News"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/publications"));

        Publication updated = publicationRepository.findById(testPublication.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Daily News");
    }

    @Test
    void testEditNonExistentPublication_returns404() throws Exception {
        mockMvc.perform(get("/admin/publications/99999/edit")
                .with(ADMIN_USER))
            .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateNonExistentPublication_returns404() throws Exception {
        mockMvc.perform(post("/admin/publications/99999")
                .with(ADMIN_USER)
                .with(csrf())
                .param("name", "Some Name"))
            .andExpect(status().isNotFound());
    }

    // --- Delete ---

    @Test
    void testDeleteConfirmationPage_shown() throws Exception {
        mockMvc.perform(get("/admin/publications/" + testPublication.getId() + "/delete")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Daily News")));
    }

    @Test
    void testDeletePublication_success_noIssues() throws Exception {
        long countBefore = publicationRepository.count();

        mockMvc.perform(post("/admin/publications/" + testPublication.getId() + "/delete")
                .with(ADMIN_USER)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/publications"))
            .andExpect(flash().attribute("successMessage", "Publication deleted successfully"));

        assertThat(publicationRepository.count()).isEqualTo(countBefore - 1);
        assertThat(publicationRepository.findById(testPublication.getId())).isEmpty();

        // Verify audit log entry
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("deleted") &&
            log.getMessage().contains("Daily News"));
    }

    @Test
    void testDeletePublication_rejected_hasIssues() throws Exception {
        // Create an Issue associated with testPublication
        Issue issue = new Issue();
        issue.setPublication(testPublication);
        issue.setIssueNumber("2024/01");
        issue.setPublicationDate(LocalDate.of(2024, 1, 15));
        issue.setUnitPrice(new BigDecimal("3.50"));
        issueRepository.save(issue);

        long countBefore = publicationRepository.count();

        mockMvc.perform(post("/admin/publications/" + testPublication.getId() + "/delete")
                .with(ADMIN_USER)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "This publication cannot be deleted because it has existing issues.")));

        // Publication not deleted
        assertThat(publicationRepository.count()).isEqualTo(countBefore);
        assertThat(publicationRepository.findById(testPublication.getId())).isPresent();
    }

    @Test
    void testDeleteNonExistentPublication_returns404() throws Exception {
        mockMvc.perform(get("/admin/publications/99999/delete")
                .with(ADMIN_USER))
            .andExpect(status().isNotFound());
    }

    @Test
    void testDeletePostNonExistentPublication_returns404() throws Exception {
        mockMvc.perform(post("/admin/publications/99999/delete")
                .with(ADMIN_USER)
                .with(csrf()))
            .andExpect(status().isNotFound());
    }

    // --- Audit ---

    @Test
    void testAuditLog_noEntryOnValidationFailure() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/admin/publications")
                .with(ADMIN_USER)
                .with(csrf())
                .param("name", ""))
            .andExpect(status().isOk());

        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
    }

    @Test
    void testAuditLog_noEntryOnDuplicateRejection() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/admin/publications")
                .with(ADMIN_USER)
                .with(csrf())
                .param("name", "Daily News"))
            .andExpect(status().isOk());

        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
    }

    @Test
    void testAuditLog_noEntryOnRejectedDeletion() throws Exception {
        // Create an Issue associated with testPublication
        Issue issue = new Issue();
        issue.setPublication(testPublication);
        issue.setIssueNumber("2024/02");
        issue.setPublicationDate(LocalDate.of(2024, 2, 15));
        issue.setUnitPrice(new BigDecimal("4.00"));
        issueRepository.save(issue);

        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/admin/publications/" + testPublication.getId() + "/delete")
                .with(ADMIN_USER)
                .with(csrf()))
            .andExpect(status().isOk());

        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
    }
}
