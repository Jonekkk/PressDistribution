package com.example.pressdistribution;

import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.ParishRepository;
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
class ParishManagementIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Parish testParish;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user("admin@parish-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user("priest@parish-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        // Create test parish (needed before Parish Priest user)
        testParish = new Parish();
        testParish.setLocality("Test Locality");
        testParish.setName("Test Parish");
        testParish.setAddress("Test Address");
        testParish = parishRepository.save(testParish);

        // Create admin user
        User admin = new User();
        admin.setFullName("Test Admin");
        admin.setEmail("admin@parish-test.com");
        admin.setPasswordHash(passwordEncoder.encode("password"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("recovery"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        // Create parish priest user
        User priest = new User();
        priest.setFullName("Test Priest");
        priest.setEmail("priest@parish-test.com");
        priest.setPasswordHash(passwordEncoder.encode("password"));
        priest.setRecoveryCodeHash(passwordEncoder.encode("recovery"));
        priest.setRole(UserRole.PARISH_PRIEST);
        priest.setParish(testParish);
        priest.setActive(true);
        userRepository.save(priest);

        // Create additional parishes for sort testing
        Parish p1 = new Parish();
        p1.setLocality("Zebra City");
        p1.setName("Alpha Church");
        parishRepository.save(p1);

        Parish p2 = new Parish();
        p2.setLocality("Alpha City");
        p2.setName("Zebra Church");
        parishRepository.save(p2);

        Parish p3 = new Parish();
        p3.setLocality("alpha city");
        p3.setName("beta church");
        parishRepository.save(p3);
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
    }

    // --- Requirement 9.2: Unauthenticated access ---

    @Test
    void testUnauthenticatedGet_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/parishes"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testUnauthenticatedPost_withCsrf_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/admin/parishes")
                .with(csrf())
                .param("locality", "Some Locality")
                .param("name", "Some Name"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testUnauthenticatedPost_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/admin/parishes")
                .param("locality", "Some Locality")
                .param("name", "Some Name"))
            .andExpect(status().isForbidden());
    }

    // --- Requirement 9.3: Parish Priest forbidden ---

    @Test
    void testParishPriest_getList_returns403() throws Exception {
        mockMvc.perform(get("/admin/parishes")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_getNew_returns403() throws Exception {
        mockMvc.perform(get("/admin/parishes/new")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_getEdit_returns403() throws Exception {
        mockMvc.perform(get("/admin/parishes/" + testParish.getId() + "/edit")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_postCreate_returns403() throws Exception {
        mockMvc.perform(post("/admin/parishes")
                .with(PRIEST_USER)
                .with(csrf())
                .param("locality", "New Locality")
                .param("name", "New Name"))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_postUpdate_returns403() throws Exception {
        mockMvc.perform(post("/admin/parishes/" + testParish.getId())
                .with(PRIEST_USER)
                .with(csrf())
                .param("locality", "Hacked Locality")
                .param("name", "Hacked Name"))
            .andExpect(status().isForbidden());
    }

    // --- Requirement 9.4: Admin GET list ---

    @Test
    void testAdminGetList_returns200WithTable() throws Exception {
        mockMvc.perform(get("/admin/parishes")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Locality")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Parish")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<table")));
    }

    // --- Requirement 9.5: Admin create parish ---

    @Test
    void testAdminCreateParish_success() throws Exception {
        long parishCountBefore = parishRepository.count();

        mockMvc.perform(post("/admin/parishes")
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", "New Locality")
                .param("name", "New Parish")
                .param("address", "New Address"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/parishes"))
            .andExpect(flash().attribute("successMessage", "Parish created successfully"));

        // Verify parish created in DB
        assertThat(parishRepository.count()).isEqualTo(parishCountBefore + 1);
        Parish created = parishRepository.findByLocalityAndName("New Locality", "New Parish").orElse(null);
        assertThat(created).isNotNull();
        assertThat(created.getAddress()).isEqualTo("New Address");

        // Verify audit log entry
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("created") &&
            log.getMessage().contains("New Locality") &&
            log.getMessage().contains("New Parish"));
    }

    // --- Requirement 9.6: Admin update parish ---

    @Test
    void testAdminUpdateParish_success() throws Exception {
        mockMvc.perform(post("/admin/parishes/" + testParish.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", "Updated Locality")
                .param("name", "Updated Name")
                .param("address", "Updated Address"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/parishes"))
            .andExpect(flash().attribute("successMessage", "Parish updated successfully"));

        // Verify parish updated in DB
        Parish updated = parishRepository.findById(testParish.getId()).orElseThrow();
        assertThat(updated.getLocality()).isEqualTo("Updated Locality");
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getAddress()).isEqualTo("Updated Address");

        // Verify audit log entry
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("updated") &&
            log.getMessage().contains("Updated Locality") &&
            log.getMessage().contains("Updated Name"));
    }

    // --- Requirement 9.7: Duplicate rejection ---

    @Test
    void testDuplicateCreate_rejected() throws Exception {
        long parishCountBefore = parishRepository.count();

        mockMvc.perform(post("/admin/parishes")
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", "Test Locality")
                .param("name", "Test Parish"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("already exists")));

        // No new parish
        assertThat(parishRepository.count()).isEqualTo(parishCountBefore);

        // No audit log entry for the attempted operation
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).noneMatch(log ->
            log.getMessage().contains("created") &&
            log.getMessage().contains("Test Locality") &&
            log.getMessage().contains("Test Parish"));
    }

    @Test
    void testDuplicateUpdate_rejected() throws Exception {
        // Try to update testParish to have same locality+name as "Zebra City"/"Alpha Church"
        mockMvc.perform(post("/admin/parishes/" + testParish.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", "Zebra City")
                .param("name", "Alpha Church"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("already exists")));

        // Parish unchanged
        Parish unchanged = parishRepository.findById(testParish.getId()).orElseThrow();
        assertThat(unchanged.getLocality()).isEqualTo("Test Locality");
        assertThat(unchanged.getName()).isEqualTo("Test Parish");

        // No audit log for the attempted update
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).noneMatch(log ->
            log.getMessage().contains("updated") &&
            log.getMessage().contains("Zebra City") &&
            log.getMessage().contains("Alpha Church"));
    }

    // --- Requirement 9.8: Validation errors ---

    @Test
    void testBlankLocality_validationError() throws Exception {
        mockMvc.perform(post("/admin/parishes")
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", "")
                .param("name", "Some Name"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Locality is required")));
    }

    @Test
    void testBlankName_validationError() throws Exception {
        mockMvc.perform(post("/admin/parishes")
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", "Some Locality")
                .param("name", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Parish name is required")));
    }

    @Test
    void testOverLengthLocality_validationError() throws Exception {
        String longLocality = "a".repeat(151);
        mockMvc.perform(post("/admin/parishes")
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", longLocality)
                .param("name", "Some Name"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Locality must not exceed 150 characters")));
    }

    @Test
    void testOverLengthName_validationError() throws Exception {
        String longName = "a".repeat(256);
        mockMvc.perform(post("/admin/parishes")
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", "Some Locality")
                .param("name", longName))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Parish name must not exceed 255 characters")));
    }

    @Test
    void testOverLengthAddress_validationError() throws Exception {
        String longAddress = "a".repeat(501);
        mockMvc.perform(post("/admin/parishes")
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", "Some Locality")
                .param("name", "Some Name")
                .param("address", longAddress))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Address must not exceed 500 characters")));
    }

    // --- Requirement 9.9: Non-existent parish ---

    @Test
    void testEditNonExistentParish_returns404() throws Exception {
        mockMvc.perform(get("/admin/parishes/99999/edit")
                .with(ADMIN_USER))
            .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateNonExistentParish_returns404() throws Exception {
        mockMvc.perform(post("/admin/parishes/99999")
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", "Some Locality")
                .param("name", "Some Name"))
            .andExpect(status().isNotFound());

        // No audit log for non-existent parish update
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).noneMatch(log ->
            log.getMessage().contains("updated") &&
            log.getMessage().contains("Some Locality") &&
            log.getMessage().contains("Some Name"));
    }

    // --- Requirement 9.10: CSRF protection ---

    @Test
    void testMissingCsrf_returns403() throws Exception {
        mockMvc.perform(post("/admin/parishes")
                .with(ADMIN_USER)
                .param("locality", "New Locality")
                .param("name", "New Parish"))
            .andExpect(status().isForbidden());
    }

    // --- Requirement 9.14: List sorting (case-insensitive) ---

    @Test
    void testListSorting_caseInsensitive() throws Exception {
        String html = mockMvc.perform(get("/admin/parishes")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Expected order (case-insensitive by locality, then name):
        // "Alpha City" / "Zebra Church"
        // "alpha city" / "beta church"
        // "Test Locality" / "Test Parish"
        // "Zebra City" / "Alpha Church"

        int posAlphaCity = html.indexOf("Alpha City");
        int posTestLocality = html.indexOf("Test Locality");
        int posZebraCity = html.indexOf("Zebra City");

        assertThat(posAlphaCity).isLessThan(posTestLocality);
        assertThat(posTestLocality).isLessThan(posZebraCity);
    }

    // --- Requirement 9.15: Em dash for null address ---

    @Test
    void testEmDashForNullAddress() throws Exception {
        // "Zebra City" / "Alpha Church" has no address (null)
        mockMvc.perform(get("/admin/parishes")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&mdash;")));
    }

    // --- Requirement 9.16: Edit form pre-populated ---

    @Test
    void testEditFormPrePopulated() throws Exception {
        mockMvc.perform(get("/admin/parishes/" + testParish.getId() + "/edit")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Locality")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Parish")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Address")));
    }

    // --- Requirement 9.19: Update own locality+name without change succeeds ---

    @Test
    void testUpdateOwnLocalityName_succeeds() throws Exception {
        mockMvc.perform(post("/admin/parishes/" + testParish.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", "Test Locality")
                .param("name", "Test Parish")
                .param("address", "Different Address"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/parishes"));

        Parish updated = parishRepository.findById(testParish.getId()).orElseThrow();
        assertThat(updated.getAddress()).isEqualTo("Different Address");
    }

    // --- Requirement 9.18: Blank address stored as null ---

    @Test
    void testBlankAddress_storedAsNull() throws Exception {
        // testParish has "Test Address", update with blank address
        mockMvc.perform(post("/admin/parishes/" + testParish.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("locality", "Test Locality")
                .param("name", "Test Parish")
                .param("address", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/parishes"));

        Parish updated = parishRepository.findById(testParish.getId()).orElseThrow();
        assertThat(updated.getAddress()).isNull();
    }

    // --- Requirement 9.13: No parishes → empty table body ---

    @Test
    void testNoParishes_emptyTableBody() throws Exception {
        // Delete all test data (users first since they reference parishes)
        userRepository.deleteAll();
        parishRepository.deleteAll();

        mockMvc.perform(get("/admin/parishes")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<thead>")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Locality")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Parish name")));
    }
}
