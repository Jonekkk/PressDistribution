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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
class UserManagementIntegrationTest {

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

    private Parish firstParish;
    private Parish secondParish;
    private User adminUser;
    private User priestUser;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user("admin@user-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user("priest@user-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        firstParish = new Parish();
        firstParish.setLocality("Test Locality");
        firstParish.setName("Main Parish");
        firstParish = parishRepository.save(firstParish);

        secondParish = new Parish();
        secondParish.setLocality("Other Locality");
        secondParish.setName("Second Parish");
        secondParish = parishRepository.save(secondParish);

        adminUser = new User();
        adminUser.setFullName("Test Admin");
        adminUser.setEmail("admin@user-test.com");
        adminUser.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
        adminUser.setRecoveryCodeHash(passwordEncoder.encode("RECOVERYCODE1234"));
        adminUser.setRole(UserRole.ADMINISTRATOR);
        adminUser.setParish(null);
        adminUser.setActive(true);
        adminUser = userRepository.save(adminUser);

        priestUser = new User();
        priestUser.setFullName("Test Priest");
        priestUser.setEmail("priest@user-test.com");
        priestUser.setPasswordHash(passwordEncoder.encode("PriestPass123!"));
        priestUser.setRecoveryCodeHash(passwordEncoder.encode("RECOVERYCODE5678"));
        priestUser.setRole(UserRole.PARISH_PRIEST);
        priestUser.setParish(firstParish);
        priestUser.setActive(true);
        priestUser = userRepository.save(priestUser);
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
    }

    // ===================== 1. ACCESS CONTROL TESTS =====================

    @Test
    void testUnauthenticatedGet_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/users"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testUnauthenticatedPost_withCsrf_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/admin/users")
                .with(csrf())
                .param("fullName", "New User")
                .param("email", "new@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "ValidPass123!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testPost_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .param("fullName", "New User")
                .param("email", "new@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "ValidPass123!"))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_getList_returns403() throws Exception {
        mockMvc.perform(get("/admin/users")
                .with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    @Test
    void testParishPriest_postCreate_returns403() throws Exception {
        mockMvc.perform(post("/admin/users")
                .with(PRIEST_USER)
                .with(csrf())
                .param("fullName", "Hacked User")
                .param("email", "hack@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "ValidPass123!"))
            .andExpect(status().isForbidden());
    }

    // ===================== 2. USER LIST TESTS =====================

    @Test
    void testAdminGetList_returns200() throws Exception {
        mockMvc.perform(get("/admin/users")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Admin")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Priest")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<table")));
    }

    @Test
    void testAdminGetList_caseInsensitiveSorting() throws Exception {
        // Create users with varying cases
        User zara = new User();
        zara.setFullName("Zara User");
        zara.setEmail("zara@test.com");
        zara.setPasswordHash(passwordEncoder.encode("ZaraPass123!"));
        zara.setRecoveryCodeHash(passwordEncoder.encode("RECOVERY12345678"));
        zara.setRole(UserRole.ADMINISTRATOR);
        zara.setParish(null);
        zara.setActive(true);
        userRepository.save(zara);

        User alpha = new User();
        alpha.setFullName("alpha User");
        alpha.setEmail("alpha@test.com");
        alpha.setPasswordHash(passwordEncoder.encode("AlphaPass123!"));
        alpha.setRecoveryCodeHash(passwordEncoder.encode("RECOVERY12345679"));
        alpha.setRole(UserRole.ADMINISTRATOR);
        alpha.setParish(null);
        alpha.setActive(true);
        userRepository.save(alpha);

        User beta = new User();
        beta.setFullName("Beta User");
        beta.setEmail("beta@test.com");
        beta.setPasswordHash(passwordEncoder.encode("BetaPass123!"));
        beta.setRecoveryCodeHash(passwordEncoder.encode("RECOVERY12345680"));
        beta.setRole(UserRole.ADMINISTRATOR);
        beta.setParish(null);
        beta.setActive(true);
        userRepository.save(beta);

        String html = mockMvc.perform(get("/admin/users")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Expected case-insensitive order: alpha, Beta, Test Admin, Test Priest, Zara
        int posAlpha = html.indexOf("alpha User");
        int posBeta = html.indexOf("Beta User");
        int posZara = html.indexOf("Zara User");

        assertThat(posAlpha).isLessThan(posBeta);
        assertThat(posBeta).isLessThan(posZara);
    }

    // ===================== 3. CREATION TESTS =====================

    @Test
    void testCreateAdminUser_success() throws Exception {
        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "New Admin")
                .param("email", "newadmin@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "SecurePass123!")
                .param("active", "true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users/credentials"));

        User created = userRepository.findByEmail("newadmin@test.com").orElse(null);
        assertThat(created).isNotNull();
        assertThat(created.getRole()).isEqualTo(UserRole.ADMINISTRATOR);
        assertThat(created.getParish()).isNull();
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void testCreateParishPriestUser_success() throws Exception {
        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "New Priest")
                .param("email", "newpriest@test.com")
                .param("role", "PARISH_PRIEST")
                .param("parishId", firstParish.getId().toString())
                .param("password", "SecurePass123!")
                .param("active", "true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users/credentials"));

        User created = userRepository.findByEmail("newpriest@test.com").orElse(null);
        assertThat(created).isNotNull();
        assertThat(created.getRole()).isEqualTo(UserRole.PARISH_PRIEST);
        assertThat(created.getParish()).isNotNull();
        assertThat(created.getParish().getId()).isEqualTo(firstParish.getId());
    }

    @Test
    void testCreateUser_roleParishInvariant_adminWithParish() throws Exception {
        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Bad Admin")
                .param("email", "badadmin@test.com")
                .param("role", "ADMINISTRATOR")
                .param("parishId", firstParish.getId().toString())
                .param("password", "SecurePass123!")
                .param("active", "true"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("must not be assigned")));

        assertThat(userRepository.findByEmail("badadmin@test.com")).isEmpty();
    }

    @Test
    void testCreateUser_roleParishInvariant_priestWithoutParish() throws Exception {
        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Bad Priest")
                .param("email", "badpriest@test.com")
                .param("role", "PARISH_PRIEST")
                .param("password", "SecurePass123!")
                .param("active", "true"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("must be assigned")));

        assertThat(userRepository.findByEmail("badpriest@test.com")).isEmpty();
    }

    @Test
    void testCreateUser_duplicateEmail_rejected() throws Exception {
        // Use existing admin email with different case
        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Duplicate")
                .param("email", "ADMIN@user-test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "SecurePass123!")
                .param("active", "true"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    void testCreateUser_passwordPolicy_tooShort() throws Exception {
        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Short Pass")
                .param("email", "shortpass@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "Short1!")
                .param("active", "true"))
            .andExpect(status().isOk());

        assertThat(userRepository.findByEmail("shortpass@test.com")).isEmpty();
    }

    @Test
    void testCreateUser_passwordNotRepopulatedOnError() throws Exception {
        String submittedPassword = "SecurePass123!";

        String html = mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Duplicate")
                .param("email", "admin@user-test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", submittedPassword)
                .param("active", "true"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // The submitted password value should not appear in the response HTML
        assertThat(html).doesNotContain(submittedPassword);
    }

    @Test
    void testCreateUser_bcryptHashesStored() throws Exception {
        String plainPassword = "SecurePass123!";

        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Hash Test")
                .param("email", "hashtest@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", plainPassword)
                .param("active", "true"))
            .andExpect(status().is3xxRedirection());

        User created = userRepository.findByEmail("hashtest@test.com").orElseThrow();
        assertThat(created.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches(plainPassword, created.getPasswordHash())).isTrue();
        assertThat(created.getRecoveryCodeHash()).startsWith("$2a$");
    }

    // ===================== 4. EDIT TESTS =====================

    @Test
    void testEditUser_success() throws Exception {
        mockMvc.perform(post("/admin/users/" + adminUser.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Updated Admin")
                .param("email", "admin@user-test.com")
                .param("role", "ADMINISTRATOR")
                .param("active", "true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attribute("successMessage", "User updated successfully"));

        User updated = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(updated.getFullName()).isEqualTo("Updated Admin");
    }

    @Test
    void testEditUser_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/admin/users/99999/edit")
                .with(ADMIN_USER))
            .andExpect(status().isNotFound());
    }

    @Test
    void testEditUser_blankPhone_storedAsNull() throws Exception {
        mockMvc.perform(post("/admin/users/" + adminUser.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Test Admin")
                .param("email", "admin@user-test.com")
                .param("role", "ADMINISTRATOR")
                .param("phoneNumber", "")
                .param("active", "true"))
            .andExpect(status().is3xxRedirection());

        User updated = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(updated.getPhoneNumber()).isNull();
    }

    @Test
    void testEditUser_duplicateEmailExcludingSelf_rejected() throws Exception {
        // Try to change admin email to priest email
        mockMvc.perform(post("/admin/users/" + adminUser.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Test Admin")
                .param("email", "priest@user-test.com")
                .param("role", "ADMINISTRATOR")
                .param("active", "true"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    void testEditUser_ownEmailUnchanged_succeeds() throws Exception {
        mockMvc.perform(post("/admin/users/" + adminUser.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Test Admin Changed")
                .param("email", "admin@user-test.com")
                .param("role", "ADMINISTRATOR")
                .param("active", "true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    void testEditForm_neverShowsPasswordOrHash() throws Exception {
        String html = mockMvc.perform(get("/admin/users/" + adminUser.getId() + "/edit")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Must not contain password hash
        assertThat(html).doesNotContain(adminUser.getPasswordHash());
        assertThat(html).doesNotContain("$2a$");
    }

    // ===================== 5. LAST ADMIN PROTECTION TESTS =====================

    @Test
    void testLastAdmin_deactivation_blocked() throws Exception {
        // adminUser is the only active administrator
        mockMvc.perform(post("/admin/users/" + adminUser.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Test Admin")
                .param("email", "admin@user-test.com")
                .param("role", "ADMINISTRATOR")
                .param("active", "false"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "at least one active administrator must remain")));

        User notDeactivated = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(notDeactivated.isActive()).isTrue();
    }

    @Test
    void testLastAdmin_roleChange_blocked() throws Exception {
        mockMvc.perform(post("/admin/users/" + adminUser.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Test Admin")
                .param("email", "admin@user-test.com")
                .param("role", "PARISH_PRIEST")
                .param("parishId", firstParish.getId().toString())
                .param("active", "true"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "at least one active administrator must remain")));

        User unchanged = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(unchanged.getRole()).isEqualTo(UserRole.ADMINISTRATOR);
    }

    @Test
    void testLastAdmin_noAuditLogCreated() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/admin/users/" + adminUser.getId())
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Test Admin")
                .param("email", "admin@user-test.com")
                .param("role", "ADMINISTRATOR")
                .param("active", "false"))
            .andExpect(status().isOk());

        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
    }

    // ===================== 6. PASSWORD RESET TESTS =====================

    @Test
    void testPasswordReset_hashesChange() throws Exception {
        String oldPasswordHash = priestUser.getPasswordHash();
        String oldRecoveryHash = priestUser.getRecoveryCodeHash();

        mockMvc.perform(post("/admin/users/" + priestUser.getId() + "/password-reset")
                .with(ADMIN_USER)
                .with(csrf())
                .param("password", "NewPassword123!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users/credentials"));

        User updated = userRepository.findById(priestUser.getId()).orElseThrow();
        assertThat(updated.getPasswordHash()).isNotEqualTo(oldPasswordHash);
        assertThat(passwordEncoder.matches("NewPassword123!", updated.getPasswordHash())).isTrue();
    }

    @Test
    void testPasswordReset_newRecoveryCodeGenerated() throws Exception {
        String oldRecoveryHash = priestUser.getRecoveryCodeHash();

        mockMvc.perform(post("/admin/users/" + priestUser.getId() + "/password-reset")
                .with(ADMIN_USER)
                .with(csrf())
                .param("password", "NewPassword123!"))
            .andExpect(status().is3xxRedirection());

        User updated = userRepository.findById(priestUser.getId()).orElseThrow();
        assertThat(updated.getRecoveryCodeHash()).isNotEqualTo(oldRecoveryHash);
    }

    @Test
    void testPasswordReset_redirectsToCredentials() throws Exception {
        mockMvc.perform(post("/admin/users/" + priestUser.getId() + "/password-reset")
                .with(ADMIN_USER)
                .with(csrf())
                .param("password", "NewPassword123!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users/credentials"));
    }

    // ===================== 7. RECOVERY-CODE RESET TESTS =====================

    @Test
    void testRecoveryCodeReset_hashChanges() throws Exception {
        String oldRecoveryHash = priestUser.getRecoveryCodeHash();

        mockMvc.perform(post("/admin/users/" + priestUser.getId() + "/recovery-code-reset")
                .with(ADMIN_USER)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        User updated = userRepository.findById(priestUser.getId()).orElseThrow();
        assertThat(updated.getRecoveryCodeHash()).isNotEqualTo(oldRecoveryHash);
    }

    @Test
    void testRecoveryCodeReset_redirectsToCredentials() throws Exception {
        mockMvc.perform(post("/admin/users/" + priestUser.getId() + "/recovery-code-reset")
                .with(ADMIN_USER)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users/credentials"));
    }

    // ===================== 8. ONE-TIME CREDENTIALS TESTS =====================

    @Test
    void testCredentials_sessionBasedDelivery() throws Exception {
        // Create a user and follow redirect to credentials
        MvcResult createResult = mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Cred Test")
                .param("email", "credtest@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "SecurePass123!")
                .param("active", "true"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        MockHttpSession session = (MockHttpSession) createResult.getRequest().getSession();

        // Follow redirect to credentials page
        String html = mockMvc.perform(get("/admin/users/credentials")
                .session(session)
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Cred Test");
        assertThat(html).contains("credtest@test.com");
        assertThat(html).contains("SecurePass123!");
    }

    @Test
    void testCredentials_cacheHeaders() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Cache Test")
                .param("email", "cachetest@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "SecurePass123!")
                .param("active", "true"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        MockHttpSession session = (MockHttpSession) createResult.getRequest().getSession();

        mockMvc.perform(get("/admin/users/credentials")
                .session(session)
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Pragma", "no-cache"));
    }

    @Test
    void testCredentials_secondRequest_redirectsAway() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "One Time")
                .param("email", "onetime@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "SecurePass123!")
                .param("active", "true"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        MockHttpSession session = (MockHttpSession) createResult.getRequest().getSession();

        // First request consumes credentials
        mockMvc.perform(get("/admin/users/credentials")
                .session(session)
                .with(ADMIN_USER))
            .andExpect(status().isOk());

        // Second request should redirect (no credentials left)
        mockMvc.perform(get("/admin/users/credentials")
                .session(session)
                .with(ADMIN_USER))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    void testCredentials_noSecretsInUrl() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "URL Test")
                .param("email", "urltest@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "SecurePass123!")
                .param("active", "true"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        String redirectUrl = createResult.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).doesNotContain("SecurePass123!");
        assertThat(redirectUrl).isEqualTo("/admin/users/credentials");
    }

    // ===================== 9. AUDIT LOGGING TESTS =====================

    @Test
    void testAuditLog_createdOnSuccess() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Audit Test")
                .param("email", "audittest@test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "SecurePass123!")
                .param("active", "true"))
            .andExpect(status().is3xxRedirection());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs.size()).isGreaterThan((int) auditCountBefore);
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("audittest@test.com"));
    }

    @Test
    void testAuditLog_notCreatedOnValidationFailure() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        // Duplicate email should not create audit log
        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Duplicate")
                .param("email", "admin@user-test.com")
                .param("role", "ADMINISTRATOR")
                .param("password", "SecurePass123!")
                .param("active", "true"))
            .andExpect(status().isOk());

        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
    }

    @Test
    void testAuditLog_noSecretsInMessage() throws Exception {
        String password = "SecurePass123!";

        mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Secret Test")
                .param("email", "secrettest@test.com")
                .param("phoneNumber", "555-1234")
                .param("role", "ADMINISTRATOR")
                .param("password", password)
                .param("active", "true"))
            .andExpect(status().is3xxRedirection());

        User created = userRepository.findByEmail("secrettest@test.com").orElseThrow();
        List<AuditLog> logs = auditLogRepository.findAll();

        for (AuditLog log : logs) {
            assertThat(log.getMessage()).doesNotContain(password);
            assertThat(log.getMessage()).doesNotContain(created.getPasswordHash());
            assertThat(log.getMessage()).doesNotContain(created.getRecoveryCodeHash());
            assertThat(log.getMessage()).doesNotContain("555-1234");
        }
    }

    // ===================== 10. PASSWORD GENERATION ENDPOINT TESTS =====================

    @Test
    void testGeneratePassword_returnsFormWithPassword() throws Exception {
        String html = mockMvc.perform(post("/admin/users/generate-password")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Gen Test")
                .param("email", "gentest@test.com")
                .param("role", "ADMINISTRATOR"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Should contain a value attribute with a 16-char password
        // The form should be rendered (not a redirect)
        assertThat(html).contains("gentest@test.com");
        assertThat(html).contains("Gen Test");
        // Password field should have a value (generated password is 16 chars)
        assertThat(html).containsPattern("value=\"[^\"]{16}\"");
    }

    @Test
    void testGeneratePassword_setsNoCacheHeader() throws Exception {
        mockMvc.perform(post("/admin/users/generate-password")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Cache Gen")
                .param("email", "cachegen@test.com")
                .param("role", "ADMINISTRATOR"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void testGeneratePassword_doesNotCreateUserOrAuditLog() throws Exception {
        long userCountBefore = userRepository.count();
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/admin/users/generate-password")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "No Persist")
                .param("email", "nopersist@test.com")
                .param("role", "ADMINISTRATOR"))
            .andExpect(status().isOk());

        assertThat(userRepository.count()).isEqualTo(userCountBefore);
        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
    }
}
