package com.example.pressdistribution;

import com.example.pressdistribution.config.BootstrapAdminProperties;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.UserRepository;
import com.example.pressdistribution.config.BootstrapAdminInitializer;
import com.example.pressdistribution.service.BootstrapAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.bootstrap-admin.email=",
    "app.bootstrap-admin.full-name=",
    "app.bootstrap-admin.password="
})
class BootstrapAdminServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Prevent the startup ApplicationRunner from executing the real bootstrap logic
    // (which now fails fast on blank admin properties used by this test).
    @MockitoBean
    private BootstrapAdminInitializer bootstrapAdminInitializer;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    private BootstrapAdminService createServiceWithProperties(String fullName, String email, String password) {
        BootstrapAdminProperties props = new BootstrapAdminProperties(fullName, email, password);
        return new BootstrapAdminService(props, userRepository, auditLogRepository, passwordEncoder);
    }

    @Test
    void createsAdminWhenEmailNotExists() {
        BootstrapAdminService service = createServiceWithProperties("Admin User", "admin@test.com", "secret123");
        service.createBootstrapAdminIfNeeded();

        Optional<User> userOpt = userRepository.findByEmail("admin@test.com");
        assertThat(userOpt).isPresent();

        User user = userOpt.get();
        assertThat(user.getFullName()).isEqualTo("Admin User");
        assertThat(user.getEmail()).isEqualTo("admin@test.com");
        assertThat(user.getRole()).isEqualTo(UserRole.ADMINISTRATOR);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getParish()).isNull();
        assertThat(user.getPasswordHash()).isNotNull();
        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(passwordEncoder.matches("secret123", user.getPasswordHash())).isTrue();
        assertThat(user.getRecoveryCodeHash()).isNotNull();
        assertThat(user.getRecoveryCodeHash()).isNotBlank();

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getMessage()).contains("admin@test.com");
        assertThat(logs.get(0).getMessage()).doesNotContain("secret123");
    }

    @Test
    void skipsWhenEmailAlreadyExists() {
        // Pre-insert user
        User existing = new User();
        existing.setFullName("Original Name");
        existing.setEmail("admin@test.com");
        existing.setPasswordHash(passwordEncoder.encode("originalpass"));
        existing.setRecoveryCodeHash(passwordEncoder.encode("placeholder"));
        existing.setRole(UserRole.ADMINISTRATOR);
        existing.setActive(true);
        userRepository.save(existing);

        long userCountBefore = userRepository.count();

        BootstrapAdminService service = createServiceWithProperties("New Name", "admin@test.com", "newpass");
        service.createBootstrapAdminIfNeeded();

        // Verify no duplicate created
        assertThat(userRepository.count()).isEqualTo(userCountBefore);

        // Verify existing user remains unchanged
        User unchanged = userRepository.findByEmail("admin@test.com").orElseThrow();
        assertThat(unchanged.getFullName()).isEqualTo("Original Name");
        assertThat(unchanged.getRole()).isEqualTo(UserRole.ADMINISTRATOR);
        assertThat(unchanged.isActive()).isTrue();
        assertThat(passwordEncoder.matches("originalpass", unchanged.getPasswordHash())).isTrue();

        // Verify no audit log entry was created
        assertThat(auditLogRepository.findAll()).isEmpty();
    }

    @Test
    void throwsWhenEmailBlank() {
        BootstrapAdminService service = createServiceWithProperties("Name", "", "pass");

        assertThatThrownBy(service::createBootstrapAdminIfNeeded)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email");

        assertThat(userRepository.count()).isZero();
        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void throwsWhenPasswordBlank() {
        BootstrapAdminService service = createServiceWithProperties("Admin User", "admin@test.com", "");

        assertThatThrownBy(service::createBootstrapAdminIfNeeded)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password");

        assertThat(userRepository.count()).isZero();
        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void throwsWhenFullNameBlank() {
        BootstrapAdminService service = createServiceWithProperties("", "admin@test.com", "pass");

        assertThatThrownBy(service::createBootstrapAdminIfNeeded)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("full-name");

        assertThat(userRepository.count()).isZero();
        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void userAndAuditLogAreAtomicTransaction() {
        // Verify that after a successful call, both user and audit log exist together
        long usersBefore = userRepository.count();
        long logsBefore = auditLogRepository.count();

        BootstrapAdminService service = createServiceWithProperties("Admin User", "admin@test.com", "secret123");
        service.createBootstrapAdminIfNeeded();

        long usersAfter = userRepository.count();
        long logsAfter = auditLogRepository.count();

        // Both must have been created together
        assertThat(usersAfter - usersBefore).isEqualTo(1);
        assertThat(logsAfter - logsBefore).isEqualTo(1);

        // Verify the audit log references the created user's email
        User createdUser = userRepository.findByEmail("admin@test.com").orElseThrow();
        AuditLog auditLog = auditLogRepository.findAll().get(0);
        assertThat(auditLog.getMessage()).contains(createdUser.getEmail());
        assertThat(auditLog.getCreatedAt()).isNotNull();
    }
}
