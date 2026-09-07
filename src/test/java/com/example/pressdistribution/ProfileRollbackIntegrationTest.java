package com.example.pressdistribution;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ProfileRollbackIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @MockitoBean(enforceOverride = true)
    AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    private User adminUser;
    private User priestUser;
    private Parish testParish;

    private static final String ADMIN_EMAIL = "admin@profile-rollback-test.com";
    private static final String ADMIN_PASSWORD = "AdminPass123!";
    private static final String PRIEST_EMAIL = "priest@profile-rollback-test.com";
    private static final String PRIEST_PASSWORD = "PriestPass123!";

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_MOCK_USER =
        user(ADMIN_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_MOCK_USER =
        user(PRIEST_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        given(auditLogRepository.save(any()))
            .willThrow(new DataIntegrityViolationException("simulated audit failure"));

        testParish = new Parish();
        testParish.setLocality("Rollback Test Locality");
        testParish.setName("Rollback Test Parish");
        testParish = parishRepository.save(testParish);

        adminUser = new User();
        adminUser.setFullName("Admin Rollback");
        adminUser.setEmail(ADMIN_EMAIL);
        adminUser.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        adminUser.setRecoveryCodeHash(passwordEncoder.encode("ABCD1234EFGH5678"));
        adminUser.setRole(UserRole.ADMINISTRATOR);
        adminUser.setParish(null);
        adminUser.setActive(true);
        adminUser = userRepository.save(adminUser);

        priestUser = new User();
        priestUser.setFullName("Priest Rollback");
        priestUser.setEmail(PRIEST_EMAIL);
        priestUser.setPasswordHash(passwordEncoder.encode(PRIEST_PASSWORD));
        priestUser.setRecoveryCodeHash(passwordEncoder.encode("WXYZ9876MNOP5432"));
        priestUser.setRole(UserRole.PARISH_PRIEST);
        priestUser.setParish(testParish);
        priestUser.setActive(true);
        priestUser = userRepository.save(priestUser);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        parishRepository.deleteAll();
    }

    @Test
    void testProfileUpdate_rollback_userUnchangedAfterAuditFailure() throws Exception {
        String originalFullName = adminUser.getFullName();
        String originalEmail = adminUser.getEmail();
        String originalPhone = adminUser.getPhoneNumber();

        mockMvc.perform(post("/profile/edit")
                .with(ADMIN_MOCK_USER)
                .with(csrf())
                .param("fullName", "Changed Name")
                .param("email", ADMIN_EMAIL)
                .param("phoneNumber", "123456789"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("The operation could not be completed")));

        // Verify the User entity is unchanged — transaction was rolled back
        User reloaded = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo(originalFullName);
        assertThat(reloaded.getEmail()).isEqualTo(originalEmail);
        assertThat(reloaded.getPhoneNumber()).isEqualTo(originalPhone);

        verify(auditLogRepository).save(any());
    }

    @Test
    void testPasswordChange_rollback_passwordAndRecoveryCodeUnchanged() throws Exception {
        String originalPasswordHash = adminUser.getPasswordHash();
        String originalRecoveryCodeHash = adminUser.getRecoveryCodeHash();

        mockMvc.perform(post("/profile/password")
                .with(ADMIN_MOCK_USER)
                .with(csrf())
                .param("currentPassword", ADMIN_PASSWORD)
                .param("newPassword", "NewSecurePass123!")
                .param("confirmPassword", "NewSecurePass123!"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("The operation could not be completed")));

        // Verify password_hash and recovery_code_hash unchanged — transaction rolled back
        User reloaded = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isEqualTo(originalPasswordHash);
        assertThat(reloaded.getRecoveryCodeHash()).isEqualTo(originalRecoveryCodeHash);

        verify(auditLogRepository).save(any());
    }

    @Test
    void testRecoveryCodeReset_rollback_recoveryCodeHashUnchanged() throws Exception {
        String originalRecoveryCodeHash = priestUser.getRecoveryCodeHash();

        mockMvc.perform(post("/profile/recovery-code-reset")
                .with(PRIEST_MOCK_USER)
                .with(csrf())
                .param("currentPassword", PRIEST_PASSWORD))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("The operation could not be completed")));

        // Verify recovery_code_hash unchanged — transaction rolled back
        User reloaded = userRepository.findById(priestUser.getId()).orElseThrow();
        assertThat(reloaded.getRecoveryCodeHash()).isEqualTo(originalRecoveryCodeHash);

        verify(auditLogRepository).save(any());
    }
}
