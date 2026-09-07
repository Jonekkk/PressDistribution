package com.example.pressdistribution.service;

import com.example.pressdistribution.config.BootstrapAdminProperties;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BootstrapAdminService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminService.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminService(BootstrapAdminProperties properties,
                                  UserRepository userRepository,
                                  AuditLogRepository auditLogRepository,
                                  PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void createBootstrapAdminIfNeeded() {
        String email = properties.email();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap administrator cannot be created: app.bootstrap-admin.email "
                            + "(APP_BOOTSTRAP_ADMIN_EMAIL) is missing or blank");
        }

        String fullName = properties.fullName();
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap administrator cannot be created: app.bootstrap-admin.full-name "
                            + "(APP_BOOTSTRAP_ADMIN_FULL_NAME) is missing or blank");
        }

        String password = properties.password();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap administrator cannot be created: app.bootstrap-admin.password "
                            + "(APP_BOOTSTRAP_ADMIN_PASSWORD) is missing or blank");
        }

        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }

        User admin = new User();
        admin.setFullName(fullName);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRecoveryCodeHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Bootstrap administrator created: " + email);
        auditLogRepository.save(auditLog);
        
        log.info("Bootstrap administrator created: " + email);
    }
}
