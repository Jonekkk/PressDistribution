package com.example.pressdistribution.service;

import com.example.pressdistribution.dto.PasswordChangeFormDto;
import com.example.pressdistribution.dto.ProfileEditFormDto;
import com.example.pressdistribution.dto.ProfileViewDto;
import com.example.pressdistribution.exception.InvalidCurrentPasswordException;
import com.example.pressdistribution.exception.UserValidationException;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final CredentialGeneratorService credentialGeneratorService;

    public ProfileService(UserRepository userRepository,
                          AuditLogRepository auditLogRepository,
                          PasswordEncoder passwordEncoder,
                          CredentialGeneratorService credentialGeneratorService) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.credentialGeneratorService = credentialGeneratorService;
    }

    /**
     * Loads user data for profile view (read-only).
     */
    @Transactional(readOnly = true)
    public ProfileViewDto getProfileView(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String roleName = user.getRole() == UserRole.ADMINISTRATOR ? "Administrator" : "Parish Priest";

        Parish parish = user.getParish();
        String parishDisplay = parish != null
                ? parish.getLocality() + " - " + parish.getName()
                : null;

        return new ProfileViewDto(
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                roleName,
                parishDisplay
        );
    }

    /**
     * Loads user data for edit form pre-population.
     */
    @Transactional(readOnly = true)
    public ProfileEditFormDto getProfileForEdit(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        ProfileEditFormDto dto = new ProfileEditFormDto();
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        return dto;
    }

    /**
     * Updates full name, email, and phone number. Returns true if email changed.
     *
     * @throws UserValidationException if the email is already taken by another user
     */
    @Transactional
    public boolean updateProfile(Long userId, ProfileEditFormDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(dto.getEmail(), userId)) {
            throw new UserValidationException(Map.of("email", "A user with this email already exists"));
        }

        boolean emailChanged = !user.getEmail().equalsIgnoreCase(dto.getEmail());

        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setPhoneNumber(normalizePhone(dto.getPhoneNumber()));

        userRepository.save(user);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Self-service profile update: " + user.getEmail());
        auditLogRepository.save(auditLog);

        return emailChanged;
    }

    /**
     * Changes password after verifying current password and ensuring new password differs.
     * Generates a new recovery code. Returns the plaintext recovery code.
     *
     * @throws InvalidCurrentPasswordException if current password does not match stored hash
     * @throws UserValidationException if new password matches current password
     */
    @Transactional
    public String changePassword(Long userId, PasswordChangeFormDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        if (passwordEncoder.matches(dto.getNewPassword(), user.getPasswordHash())) {
            throw new UserValidationException(
                    Map.of("newPassword", "New password must differ from the current password"));
        }

        String plainRecoveryCode = credentialGeneratorService.generateRecoveryCode();

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setRecoveryCodeHash(passwordEncoder.encode(plainRecoveryCode));

        userRepository.save(user);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Self-service password change: " + user.getEmail());
        auditLogRepository.save(auditLog);

        return plainRecoveryCode;
    }

    /**
     * Resets recovery code after verifying current password.
     * Returns the plaintext recovery code.
     *
     * @throws InvalidCurrentPasswordException if current password does not match stored hash
     */
    @Transactional
    public String resetRecoveryCode(Long userId, String currentPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        String plainRecoveryCode = credentialGeneratorService.generateRecoveryCode();

        user.setRecoveryCodeHash(passwordEncoder.encode(plainRecoveryCode));

        userRepository.save(user);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Self-service recovery code reset: " + user.getEmail());
        auditLogRepository.save(auditLog);

        return plainRecoveryCode;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone;
    }
}
