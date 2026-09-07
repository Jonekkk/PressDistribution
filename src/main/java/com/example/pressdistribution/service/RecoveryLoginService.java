package com.example.pressdistribution.service;

import com.example.pressdistribution.dto.RecoveryCodeUtil;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for unauthenticated password recovery using a recovery code.
 * Verifies email + recovery code, resets the password, and generates a new recovery code.
 */
@Service
public class RecoveryLoginService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final CredentialGeneratorService credentialGeneratorService;

    public RecoveryLoginService(UserRepository userRepository,
                                AuditLogRepository auditLogRepository,
                                PasswordEncoder passwordEncoder,
                                CredentialGeneratorService credentialGeneratorService) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.credentialGeneratorService = credentialGeneratorService;
    }

    /**
     * Attempts to reset a user's password using their email and recovery code.
     *
     * @param email        the user's email address (already normalized to lowercase)
     * @param recoveryCode the raw user-input recovery code (may contain dashes/spaces)
     * @param newPassword  the new password to set
     * @return the new plaintext recovery code on success, or empty if verification fails
     */
    @Transactional
    public Optional<String> resetPasswordWithRecoveryCode(String email, String recoveryCode, String newPassword) {
        String normalizedCode = RecoveryCodeUtil.normalizeInput(recoveryCode);

        if (normalizedCode == null || normalizedCode.isBlank()) {
            return Optional.empty();
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();

        // Inactive accounts cannot recover
        if (!user.isActive()) {
            return Optional.empty();
        }

        // Verify recovery code against stored hash
        if (!passwordEncoder.matches(normalizedCode, user.getRecoveryCodeHash())) {
            return Optional.empty();
        }

        // Success: update password and generate a new recovery code
        String newRecoveryCode = credentialGeneratorService.generateRecoveryCode();

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setRecoveryCodeHash(passwordEncoder.encode(newRecoveryCode));
        userRepository.save(user);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Password reset via recovery code: " + user.getEmail());
        auditLogRepository.save(auditLog);

        return Optional.of(newRecoveryCode);
    }
}
