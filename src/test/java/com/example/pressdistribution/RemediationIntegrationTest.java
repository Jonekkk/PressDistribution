package com.example.pressdistribution;

import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.bootstrap-admin.email=remediation-admin@test.com",
    "app.bootstrap-admin.full-name=Remediation Admin",
    "app.bootstrap-admin.password=adminPass123",
    "app.seed.enabled=false"
})
class RemediationIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private static final String RECOVERY_CODE_RAW = "ABCDEFGH23456789";
    private static final String RECOVERY_CODE_DASHED = "ABCD-EFGH-2345-6789";
    private static final String TEST_EMAIL = "recovery-user@test.com";
    private static final String OLD_PASSWORD = "OldPass123!";
    private static final String NEW_PASSWORD = "NewPass456!";

    @BeforeEach
    void setUp() {
        // Clean up test user if exists
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
    }

    private User createTestUser() {
        User user = new User();
        user.setFullName("Recovery Test User");
        user.setEmail(TEST_EMAIL);
        user.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        user.setRecoveryCodeHash(passwordEncoder.encode(RECOVERY_CODE_RAW));
        user.setRole(UserRole.ADMINISTRATOR);
        user.setActive(true);
        return userRepository.save(user);
    }

    // ===========================================
    // 1. Recovery page is anonymously accessible
    // ===========================================

    @Test
    void recoveryPage_isAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/login/recovery"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Reset Password")));
    }

    // ===========================================
    // 2. Valid recovery code changes the password
    // ===========================================

    @Test
    void validRecoveryCode_changesPassword() throws Exception {
        createTestUser();

        MvcResult result = mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login/recovery/success"))
            .andReturn();

        // Verify password was changed
        User updatedUser = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updatedUser.getPasswordHash())).isTrue();
    }

    // ===========================================
    // 3. Old password no longer works after recovery
    // ===========================================

    @Test
    void afterRecovery_oldPasswordNoLongerWorks() throws Exception {
        createTestUser();

        mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        // Old password should fail
        User updatedUser = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, updatedUser.getPasswordHash())).isFalse();
    }

    // ===========================================
    // 4. New password works after recovery
    // ===========================================

    @Test
    void afterRecovery_newPasswordWorks() throws Exception {
        createTestUser();

        mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        // Login with new password should succeed
        mockMvc.perform(post("/login")
                .param("username", TEST_EMAIL)
                .param("password", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }

    // ===========================================
    // 5. Old recovery code no longer works after recovery
    // ===========================================

    @Test
    void afterRecovery_oldRecoveryCodeNoLongerWorks() throws Exception {
        createTestUser();

        mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        // Second attempt with old code should fail
        mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", "AnotherPass789!")
                .param("confirmPassword", "AnotherPass789!")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Unable to reset password")));
    }

    // ===========================================
    // 6. A new recovery code is generated after recovery
    // ===========================================

    @Test
    void afterRecovery_newRecoveryCodeIsGenerated() throws Exception {
        createTestUser();
        String originalHash = userRepository.findByEmail(TEST_EMAIL).orElseThrow().getRecoveryCodeHash();

        mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        String newHash = userRepository.findByEmail(TEST_EMAIL).orElseThrow().getRecoveryCodeHash();
        assertThat(newHash).isNotEqualTo(originalHash);
    }

    // ===========================================
    // 7. New code is shown once and has XXXX-XXXX-XXXX-XXXX format
    // ===========================================

    @Test
    void recoverySuccess_showsFormattedRecoveryCodeOnce() throws Exception {
        createTestUser();

        MvcResult postResult = mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login/recovery/success"))
            .andReturn();

        MockHttpSession session = (MockHttpSession) postResult.getRequest().getSession(false);

        // First visit: code is shown in dashed format
        MvcResult successResult = mockMvc.perform(get("/login/recovery/success").session(session))
            .andExpect(status().isOk())
            .andReturn();

        String body = successResult.getResponse().getContentAsString();
        // Verify XXXX-XXXX-XXXX-XXXX format (4 groups of 4 chars separated by dashes)
        assertThat(body).containsPattern("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}");
    }

    // ===========================================
    // 8. Refreshing the success page does not reveal the code again
    // ===========================================

    @Test
    void recoverySuccess_refreshDoesNotShowCodeAgain() throws Exception {
        createTestUser();

        MvcResult postResult = mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        MockHttpSession session = (MockHttpSession) postResult.getRequest().getSession(false);

        // First visit — consumes the code
        mockMvc.perform(get("/login/recovery/success").session(session))
            .andExpect(status().isOk());

        // Second visit — code is gone, redirects to /login
        mockMvc.perform(get("/login/recovery/success").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    // ===========================================
    // 9. Dashed and non-dashed recovery code input both work
    // ===========================================

    @Test
    void recoveryCode_dashedInputVerifiesCorrectly() throws Exception {
        createTestUser();

        mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_DASHED)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login/recovery/success"));
    }

    @Test
    void recoveryCode_rawInputVerifiesCorrectly() throws Exception {
        createTestUser();

        mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login/recovery/success"));
    }

    // ===========================================
    // 10. Invalid email/code/inactive return same failure message
    // ===========================================

    @Test
    void recovery_invalidEmail_showsGenericError() throws Exception {
        mockMvc.perform(post("/login/recovery")
                .param("email", "nonexistent@test.com")
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Unable to reset password")));
    }

    @Test
    void recovery_invalidCode_showsGenericError() throws Exception {
        createTestUser();

        mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", "WRONGCODEWRONGCO")
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Unable to reset password")));
    }

    @Test
    void recovery_inactiveAccount_showsGenericError() throws Exception {
        User user = createTestUser();
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Unable to reset password")));
    }

    // ===========================================
    // 11. Failed sign-in creates exactly one safe audit entry
    // ===========================================

    @Test
    void failedSignIn_createsOneAuditEntry() throws Exception {
        long countBefore = auditLogRepository.count();

        mockMvc.perform(post("/login")
                .param("username", "baduser@test.com")
                .param("password", "badpassword")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));

        List<AuditLog> newEntries = auditLogRepository.findAll().stream()
                .skip(countBefore)
                .toList();

        assertThat(newEntries).hasSize(1);
        assertThat(newEntries.getFirst().getMessage()).contains("Sign-in failure");
        assertThat(newEntries.getFirst().getMessage()).contains("baduser@test.com");
    }

    // ===========================================
    // 12. No password or recovery code in audit logs
    // ===========================================

    @Test
    void recoveryAuditLog_doesNotContainSecrets() throws Exception {
        createTestUser();
        long countBefore = auditLogRepository.count();

        mockMvc.perform(post("/login/recovery")
                .param("email", TEST_EMAIL)
                .param("recoveryCode", RECOVERY_CODE_RAW)
                .param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        List<AuditLog> newEntries = auditLogRepository.findAll().stream()
                .skip(countBefore)
                .toList();

        assertThat(newEntries).isNotEmpty();
        for (AuditLog entry : newEntries) {
            assertThat(entry.getMessage()).doesNotContain(RECOVERY_CODE_RAW);
            assertThat(entry.getMessage()).doesNotContain(NEW_PASSWORD);
            assertThat(entry.getMessage()).doesNotContain(OLD_PASSWORD);
        }
    }

    @Test
    void signInFailureAuditLog_doesNotContainPassword() throws Exception {
        long countBefore = auditLogRepository.count();

        mockMvc.perform(post("/login")
                .param("username", "someone@test.com")
                .param("password", "secretPassword123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        List<AuditLog> newEntries = auditLogRepository.findAll().stream()
                .skip(countBefore)
                .toList();

        for (AuditLog entry : newEntries) {
            assertThat(entry.getMessage()).doesNotContain("secretPassword123");
        }
    }

    // ===========================================
    // 13. Issue number with hyphens is accepted
    // ===========================================

    @Test
    void issueNumber_withHyphen_isAccepted() throws Exception {
        // Login as admin
        MvcResult loginResult = mockMvc.perform(post("/login")
                .param("username", "remediation-admin@test.com")
                .param("password", "adminPass123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // First create a publication
        mockMvc.perform(post("/admin/publications")
                .session(session)
                .param("name", "Test Publication Hyphen")
                .with(csrf()))
            .andExpect(status().is3xxRedirection());

        // Find the publication ID
        var publication = mockMvc.perform(get("/admin/publications").session(session))
            .andExpect(status().isOk())
            .andReturn();

        // Create issue with hyphen in number - using the admin issue endpoint
        mockMvc.perform(post("/admin/issues")
                .session(session)
                .param("publicationId", "1")
                .param("issueNumber", "51-52")
                .param("publicationDate", "2025-01-01")
                .param("unitPrice", "5.00")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/issues"));
    }

    // ===========================================
    // 14. Invalid issue-number characters remain rejected
    // ===========================================

    @Test
    void issueNumber_withInvalidChars_isRejected() throws Exception {
        // Login as admin
        MvcResult loginResult = mockMvc.perform(post("/login")
                .param("username", "remediation-admin@test.com")
                .param("password", "adminPass123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Attempt to create issue with invalid characters (e.g. @#$)
        mockMvc.perform(post("/admin/issues")
                .session(session)
                .param("publicationId", "1")
                .param("issueNumber", "12@#$")
                .param("publicationDate", "2025-01-01")
                .param("unitPrice", "5.00")
                .with(csrf()))
            .andExpect(status().isOk()); // stays on form due to validation error
    }

    // ===========================================
    // 15. Unit price 0.00 is rejected
    // ===========================================

    @Test
    void unitPrice_zero_isRejectedAtFormValidation() throws Exception {
        // Login as admin
        MvcResult loginResult = mockMvc.perform(post("/login")
                .param("username", "remediation-admin@test.com")
                .param("password", "adminPass123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Attempt to create issue with price 0.00
        mockMvc.perform(post("/admin/issues")
                .session(session)
                .param("publicationId", "1")
                .param("issueNumber", "Test001")
                .param("publicationDate", "2025-01-01")
                .param("unitPrice", "0.00")
                .with(csrf()))
            .andExpect(status().isOk()); // stays on form due to validation error
    }

    @Test
    void unitPrice_positive_isAccepted() throws Exception {
        // Login as admin
        MvcResult loginResult = mockMvc.perform(post("/login")
                .param("username", "remediation-admin@test.com")
                .param("password", "adminPass123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Create issue with price 0.01 (minimum valid)
        mockMvc.perform(post("/admin/issues")
                .session(session)
                .param("publicationId", "1")
                .param("issueNumber", "Test002")
                .param("publicationDate", "2025-01-01")
                .param("unitPrice", "0.01")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/issues"));
    }
}
