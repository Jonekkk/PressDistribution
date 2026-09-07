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
class ProfileIntegrationTest {

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
    private User adminUser;
    private User priestUser;

    private static final String ADMIN_PASSWORD = "AdminPass123!";
    private static final String PRIEST_PASSWORD = "PriestPass123!";

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user("admin@profile-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user("priest@profile-test.com").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        testParish = new Parish();
        testParish.setLocality("Test Locality");
        testParish.setName("Main Parish");
        testParish = parishRepository.save(testParish);

        adminUser = new User();
        adminUser.setFullName("Test Admin");
        adminUser.setEmail("admin@profile-test.com");
        adminUser.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        adminUser.setRecoveryCodeHash(passwordEncoder.encode("RECOVERYCODE1234"));
        adminUser.setRole(UserRole.ADMINISTRATOR);
        adminUser.setParish(null);
        adminUser.setActive(true);
        adminUser = userRepository.save(adminUser);

        priestUser = new User();
        priestUser.setFullName("Test Priest");
        priestUser.setEmail("priest@profile-test.com");
        priestUser.setPasswordHash(passwordEncoder.encode(PRIEST_PASSWORD));
        priestUser.setRecoveryCodeHash(passwordEncoder.encode("RECOVERYCODE5678"));
        priestUser.setRole(UserRole.PARISH_PRIEST);
        priestUser.setParish(testParish);
        priestUser.setActive(true);
        priestUser = userRepository.save(priestUser);
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
    }

    // ===================== 1. SECURITY ENFORCEMENT =====================

    @Test
    void testUnauthenticatedGet_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/profile"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testUnauthenticatedPost_withCsrf_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/profile/edit")
                .with(csrf())
                .param("fullName", "Hacker")
                .param("email", "hacker@test.com"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void testPost_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/profile/edit")
                .with(ADMIN_USER)
                .param("fullName", "Test Admin")
                .param("email", "admin@profile-test.com"))
            .andExpect(status().isForbidden());
    }

    @Test
    void testPost_withInvalidCsrf_returns403() throws Exception {
        mockMvc.perform(post("/profile/edit")
                .with(ADMIN_USER)
                .with(csrf().useInvalidToken())
                .param("fullName", "Test Admin")
                .param("email", "admin@profile-test.com"))
            .andExpect(status().isForbidden());
    }

    // ===================== 2. PROFILE VIEW =====================

    @Test
    void testProfileView_admin_returns200WithUserData() throws Exception {
        String html = mockMvc.perform(get("/profile")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Test Admin");
        assertThat(html).contains("admin@profile-test.com");
    }

    @Test
    void testProfileView_priest_showsParishLocalityAndName() throws Exception {
        String html = mockMvc.perform(get("/profile")
                .with(PRIEST_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Test Priest");
        assertThat(html).contains("priest@profile-test.com");
        assertThat(html).contains("Test Locality");
        assertThat(html).contains("Main Parish");
    }

    @Test
    void testProfileView_admin_showsEmDashForParish() throws Exception {
        String html = mockMvc.perform(get("/profile")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Admin has no parish, should show em dash
        assertThat(html).contains("\u2014");
    }

    @Test
    void testProfileView_doesNotContainPasswordOrRecoveryCodeHash() throws Exception {
        String html = mockMvc.perform(get("/profile")
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain(adminUser.getPasswordHash());
        assertThat(html).doesNotContain(adminUser.getRecoveryCodeHash());
        assertThat(html).doesNotContain("$2a$");
    }

    // ===================== 3. PROFILE EDIT HAPPY PATH =====================

    @Test
    void testProfileEdit_updateFullNameAndPhone_redirectsToProfile() throws Exception {
        mockMvc.perform(post("/profile/edit")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Updated Admin Name")
                .param("email", "admin@profile-test.com")
                .param("phoneNumber", "555-1234"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/profile"));

        User updated = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(updated.getFullName()).isEqualTo("Updated Admin Name");
        assertThat(updated.getPhoneNumber()).isEqualTo("555-1234");
    }

    @Test
    void testProfileEdit_emailChange_redirectsToLoginWithProfileUpdated() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/profile/edit")
                .session(session)
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Test Admin")
                .param("email", "newemail@profile-test.com")
                .param("phoneNumber", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?profileUpdated"));

        User updated = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(updated.getEmail()).isEqualTo("newemail@profile-test.com");

        // Session should be invalidated
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void testProfileEdit_nonEmailChange_preservesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/profile/edit")
                .session(session)
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "New Name Only")
                .param("email", "admin@profile-test.com")
                .param("phoneNumber", "123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/profile"));

        assertThat(session.isInvalid()).isFalse();
    }

    // ===================== 4. PROFILE EDIT VALIDATION =====================

    @Test
    void testProfileEdit_blankFullName_rejected() throws Exception {
        mockMvc.perform(post("/profile/edit")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "")
                .param("email", "admin@profile-test.com"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Full name is required")));

        User unchanged = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(unchanged.getFullName()).isEqualTo("Test Admin");
    }

    @Test
    void testProfileEdit_invalidEmail_rejected() throws Exception {
        mockMvc.perform(post("/profile/edit")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Test Admin")
                .param("email", "not-an-email"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("valid email")));

        User unchanged = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(unchanged.getEmail()).isEqualTo("admin@profile-test.com");
    }

    @Test
    void testProfileEdit_phoneTooLong_rejected() throws Exception {
        mockMvc.perform(post("/profile/edit")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Test Admin")
                .param("email", "admin@profile-test.com")
                .param("phoneNumber", "123456789012345678901"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Phone number must not exceed 20 characters")));

        User unchanged = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(unchanged.getPhoneNumber()).isNull();
    }

    @Test
    void testProfileEdit_duplicateEmail_rejected() throws Exception {
        mockMvc.perform(post("/profile/edit")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Test Admin")
                .param("email", "priest@profile-test.com"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("already exists")));

        User unchanged = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(unchanged.getEmail()).isEqualTo("admin@profile-test.com");
    }

    @Test
    void testProfileEdit_roleParishActiveParamsIgnored() throws Exception {
        mockMvc.perform(post("/profile/edit")
                .with(PRIEST_USER)
                .with(csrf())
                .param("fullName", "Updated Priest")
                .param("email", "priest@profile-test.com")
                .param("role", "ADMINISTRATOR")
                .param("parishId", "99999")
                .param("active", "false"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/profile"));

        User unchanged = userRepository.findById(priestUser.getId()).orElseThrow();
        assertThat(unchanged.getRole()).isEqualTo(UserRole.PARISH_PRIEST);
        assertThat(unchanged.getParish().getId()).isEqualTo(testParish.getId());
        assertThat(unchanged.isActive()).isTrue();
        assertThat(unchanged.getFullName()).isEqualTo("Updated Priest");
    }

    // ===================== 5. PASSWORD CHANGE =====================

    @Test
    void testPasswordChange_wrongCurrentPassword_rejected() throws Exception {
        String html = mockMvc.perform(post("/profile/password")
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", "WrongPassword123!")
                .param("newPassword", "NewSecure123!!")
                .param("confirmPassword", "NewSecure123!!"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Current password is incorrect.");
    }

    @Test
    void testPasswordChange_policyViolation_rejected() throws Exception {
        mockMvc.perform(post("/profile/password")
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD)
                .param("newPassword", "short")
                .param("confirmPassword", "short"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Password must contain")));
    }

    @Test
    void testPasswordChange_sameAsCurrent_rejected() throws Exception {
        String html = mockMvc.perform(post("/profile/password")
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD)
                .param("newPassword", ADMIN_PASSWORD)
                .param("confirmPassword", ADMIN_PASSWORD))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("New password must differ from the current password");
    }

    @Test
    void testPasswordChange_mismatchedConfirm_rejected() throws Exception {
        String html = mockMvc.perform(post("/profile/password")
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD)
                .param("newPassword", "NewSecure123!!")
                .param("confirmPassword", "DifferentPass123!!"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Passwords do not match");
    }

    @Test
    void testPasswordChange_valid_updatesHashesAndRedirects() throws Exception {
        String oldPasswordHash = adminUser.getPasswordHash();
        String oldRecoveryHash = adminUser.getRecoveryCodeHash();

        MockHttpSession session = new MockHttpSession();

        MvcResult result = mockMvc.perform(post("/profile/password")
                .session(session)
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD)
                .param("newPassword", "BrandNewPass123!")
                .param("confirmPassword", "BrandNewPass123!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/profile/recovery-code-result"))
            .andReturn();

        User updated = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(updated.getPasswordHash()).isNotEqualTo(oldPasswordHash);
        assertThat(passwordEncoder.matches("BrandNewPass123!", updated.getPasswordHash())).isTrue();
        assertThat(updated.getRecoveryCodeHash()).isNotEqualTo(oldRecoveryHash);
    }

    @Test
    void testPasswordChange_recoveryCodeResultPage_showsCodeWithCacheHeaders() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/profile/password")
                .session(session)
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD)
                .param("newPassword", "BrandNewPass123!")
                .param("confirmPassword", "BrandNewPass123!"))
            .andExpect(status().is3xxRedirection());

        MvcResult resultPage = mockMvc.perform(get("/profile/recovery-code-result")
                .session(session)
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Pragma", "no-cache"))
            .andReturn();

        String html = resultPage.getResponse().getContentAsString();
        // Recovery code is displayed in XXXX-XXXX-XXXX-XXXX format
        assertThat(html).containsPattern("[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}");
    }

    // ===================== 6. RECOVERY CODE RESET =====================

    @Test
    void testRecoveryCodeReset_wrongPassword_rejected() throws Exception {
        String html = mockMvc.perform(post("/profile/recovery-code-reset")
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", "WrongPassword123!"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Current password is incorrect.");
    }

    @Test
    void testRecoveryCodeReset_valid_updatesHash() throws Exception {
        String oldRecoveryHash = adminUser.getRecoveryCodeHash();

        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/profile/recovery-code-reset")
                .session(session)
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/profile/recovery-code-result"));

        User updated = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(updated.getRecoveryCodeHash()).isNotEqualTo(oldRecoveryHash);
    }

    @Test
    void testRecoveryCodeReset_resultPageDisplaysOnce_thenRedirects() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/profile/recovery-code-reset")
                .session(session)
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD))
            .andExpect(status().is3xxRedirection());

        // First GET shows the code
        String html = mockMvc.perform(get("/profile/recovery-code-result")
                .session(session)
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).containsPattern("[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}");

        // Second GET redirects to /profile
        mockMvc.perform(get("/profile/recovery-code-result")
                .session(session)
                .with(ADMIN_USER))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/profile"));
    }

    @Test
    void testRecoveryCodeReset_resultPage_hasCacheHeaders() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/profile/recovery-code-reset")
                .session(session)
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/profile/recovery-code-result")
                .session(session)
                .with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Pragma", "no-cache"));
    }

    // ===================== 7. AUDIT LOGGING =====================

    @Test
    void testAuditLog_profileUpdateCreatesEntry() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/profile/edit")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Audit Test Admin")
                .param("email", "admin@profile-test.com")
                .param("phoneNumber", "555-9999"))
            .andExpect(status().is3xxRedirection());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs.size()).isGreaterThan((int) auditCountBefore);
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("profile update") &&
            log.getMessage().contains("admin@profile-test.com"));
    }

    @Test
    void testAuditLog_passwordChangeCreatesEntry() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/profile/password")
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD)
                .param("newPassword", "BrandNewPass123!")
                .param("confirmPassword", "BrandNewPass123!"))
            .andExpect(status().is3xxRedirection());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs.size()).isGreaterThan((int) auditCountBefore);
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("password change") &&
            log.getMessage().contains("admin@profile-test.com"));
    }

    @Test
    void testAuditLog_recoveryCodeResetCreatesEntry() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/profile/recovery-code-reset")
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD))
            .andExpect(status().is3xxRedirection());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs.size()).isGreaterThan((int) auditCountBefore);
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("recovery code reset") &&
            log.getMessage().contains("admin@profile-test.com"));
    }

    @Test
    void testAuditLog_validationFailureCreatesNoEntry() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        // Submit invalid form (blank full name)
        mockMvc.perform(post("/profile/edit")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "")
                .param("email", "admin@profile-test.com"))
            .andExpect(status().isOk());

        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
    }

    @Test
    void testAuditLog_noSecretsInMessage() throws Exception {
        mockMvc.perform(post("/profile/password")
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD)
                .param("newPassword", "BrandNewPass123!")
                .param("confirmPassword", "BrandNewPass123!"))
            .andExpect(status().is3xxRedirection());

        List<AuditLog> logs = auditLogRepository.findAll();
        for (AuditLog log : logs) {
            assertThat(log.getMessage()).doesNotContain(ADMIN_PASSWORD);
            assertThat(log.getMessage()).doesNotContain("BrandNewPass123!");
            assertThat(log.getMessage()).doesNotContain("$2a$");
        }
    }

    // ===================== 8. SECRET-FREE URLs =====================

    @Test
    void testSecretFreeUrls_passwordChangeRedirectContainsNoSecrets() throws Exception {
        MvcResult result = mockMvc.perform(post("/profile/password")
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD)
                .param("newPassword", "BrandNewPass123!")
                .param("confirmPassword", "BrandNewPass123!"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        String redirectUrl = result.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).doesNotContain(ADMIN_PASSWORD);
        assertThat(redirectUrl).doesNotContain("BrandNewPass123!");
        assertThat(redirectUrl).isEqualTo("/profile/recovery-code-result");
    }

    @Test
    void testSecretFreeUrls_recoveryCodeResetRedirectContainsNoSecrets() throws Exception {
        MvcResult result = mockMvc.perform(post("/profile/recovery-code-reset")
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        String redirectUrl = result.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).doesNotContain(ADMIN_PASSWORD);
        assertThat(redirectUrl).isEqualTo("/profile/recovery-code-result");
    }

    @Test
    void testSecretFreeUrls_secondGetToResultPageContainsNoRecoveryCode() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/profile/recovery-code-reset")
                .session(session)
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD))
            .andExpect(status().is3xxRedirection());

        // Consume the code on first GET
        mockMvc.perform(get("/profile/recovery-code-result")
                .session(session)
                .with(ADMIN_USER))
            .andExpect(status().isOk());

        // Second GET - should redirect and contain no recovery code
        MvcResult secondResult = mockMvc.perform(get("/profile/recovery-code-result")
                .session(session)
                .with(ADMIN_USER))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/profile"))
            .andReturn();

        String body = secondResult.getResponse().getContentAsString();
        // Body should be empty or not contain a recovery code pattern
        assertThat(body).doesNotContainPattern("[23456789A-HJ-NP-Z]{16}");
    }

    // ===================== 9. RECOVERY CODE RESET FORM =====================

    @Test
    void testRecoveryCodeResetForm_afterFailedPassword_fieldIsPasswordTypeAndEmpty() throws Exception {
        String html = mockMvc.perform(post("/profile/recovery-code-reset")
                .with(ADMIN_USER)
                .with(csrf())
                .param("currentPassword", "WrongPassword123!"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Password field should be type="password" and empty (no value attribute with content)
        assertThat(html).contains("type=\"password\"");
        assertThat(html).doesNotContain("WrongPassword123!");
    }
}
