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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
class UserManagementRollbackIntegrationTest {

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

    private Parish testParish;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user("admin@rollback-test.com").authorities(
            new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    @BeforeEach
    void setUp() {
        given(auditLogRepository.save(any()))
            .willThrow(new DataIntegrityViolationException("simulated audit failure"));

        testParish = new Parish();
        testParish.setLocality("Rollback Locality");
        testParish.setName("Rollback Parish");
        testParish = parishRepository.save(testParish);

        User admin = new User();
        admin.setFullName("Rollback Admin");
        admin.setEmail("admin@rollback-test.com");
        admin.setPasswordHash(passwordEncoder.encode("Password123!"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("ABCD1234EFGH5678"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        parishRepository.deleteAll();
    }

    @Test
    void testCreateUser_rollback() throws Exception {
        long userCountBefore = userRepository.count();

        // The DataIntegrityViolationException from auditLogRepository.save() propagates
        // as a ServletException wrapping the original exception
        assertThatThrownBy(() ->
            mockMvc.perform(post("/admin/users")
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "New Rollback User")
                .param("email", "newuser@rollback-test.com")
                .param("role", "ADMINISTRATOR")
                .param("active", "true")
                .param("password", "SecurePass123!"))
        ).hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        // No new user should exist - transaction was rolled back
        assertThat(userRepository.count()).isEqualTo(userCountBefore);
        assertThat(userRepository.findByEmail("newuser@rollback-test.com")).isEmpty();

        verify(auditLogRepository).save(any());
    }

    @Test
    void testUpdateUser_rollback() throws Exception {
        User targetUser = new User();
        targetUser.setFullName("Original Name");
        targetUser.setEmail("target@rollback-test.com");
        targetUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        targetUser.setRecoveryCodeHash(passwordEncoder.encode("ABCD1234EFGH5678"));
        targetUser.setRole(UserRole.ADMINISTRATOR);
        targetUser.setParish(null);
        targetUser.setActive(true);
        targetUser = userRepository.save(targetUser);

        Long targetId = targetUser.getId();

        assertThatThrownBy(() ->
            mockMvc.perform(post("/admin/users/" + targetId)
                .with(ADMIN_USER)
                .with(csrf())
                .param("fullName", "Changed Name")
                .param("email", "target@rollback-test.com")
                .param("role", "ADMINISTRATOR")
                .param("active", "true"))
        ).hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        // User's fullName should remain unchanged - transaction was rolled back
        User reloaded = userRepository.findById(targetId).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Original Name");

        verify(auditLogRepository).save(any());
    }

    @Test
    void testResetPassword_rollback() throws Exception {
        User targetUser = new User();
        targetUser.setFullName("Password Reset Target");
        targetUser.setEmail("pwreset@rollback-test.com");
        targetUser.setPasswordHash(passwordEncoder.encode("OldPassword123!"));
        targetUser.setRecoveryCodeHash(passwordEncoder.encode("OLDCODE12345678"));
        targetUser.setRole(UserRole.ADMINISTRATOR);
        targetUser.setParish(null);
        targetUser.setActive(true);
        targetUser = userRepository.save(targetUser);

        Long targetId = targetUser.getId();
        String originalPasswordHash = targetUser.getPasswordHash();
        String originalRecoveryCodeHash = targetUser.getRecoveryCodeHash();

        assertThatThrownBy(() ->
            mockMvc.perform(post("/admin/users/" + targetId + "/password-reset")
                .with(ADMIN_USER)
                .with(csrf())
                .param("password", "NewSecurePass123!"))
        ).hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        // Hashes should remain unchanged - transaction was rolled back
        User reloaded = userRepository.findById(targetId).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isEqualTo(originalPasswordHash);
        assertThat(reloaded.getRecoveryCodeHash()).isEqualTo(originalRecoveryCodeHash);

        verify(auditLogRepository).save(any());
    }

    @Test
    void testResetRecoveryCode_rollback() throws Exception {
        User targetUser = new User();
        targetUser.setFullName("Recovery Code Target");
        targetUser.setEmail("rcreset@rollback-test.com");
        targetUser.setPasswordHash(passwordEncoder.encode("Password123!"));
        targetUser.setRecoveryCodeHash(passwordEncoder.encode("OLDRECOVERY12345"));
        targetUser.setRole(UserRole.ADMINISTRATOR);
        targetUser.setParish(null);
        targetUser.setActive(true);
        targetUser = userRepository.save(targetUser);

        Long targetId = targetUser.getId();
        String originalRecoveryCodeHash = targetUser.getRecoveryCodeHash();

        assertThatThrownBy(() ->
            mockMvc.perform(post("/admin/users/" + targetId + "/recovery-code-reset")
                .with(ADMIN_USER)
                .with(csrf()))
        ).hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        // Recovery code hash should remain unchanged - transaction was rolled back
        User reloaded = userRepository.findById(targetId).orElseThrow();
        assertThat(reloaded.getRecoveryCodeHash()).isEqualTo(originalRecoveryCodeHash);

        verify(auditLogRepository).save(any());
    }
}
