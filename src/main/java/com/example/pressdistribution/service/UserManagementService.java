package com.example.pressdistribution.service;

import com.example.pressdistribution.dto.CreateUserFormDto;
import com.example.pressdistribution.dto.CredentialsResult;
import com.example.pressdistribution.dto.RecoveryCodeResult;
import com.example.pressdistribution.dto.UserFormDto;
import com.example.pressdistribution.dto.UserListItemDto;
import com.example.pressdistribution.exception.LastAdministratorException;
import com.example.pressdistribution.exception.UserNotFoundException;
import com.example.pressdistribution.exception.UserValidationException;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final ParishRepository parishRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final CredentialGeneratorService credentialGeneratorService;

    public UserManagementService(UserRepository userRepository,
                                  ParishRepository parishRepository,
                                  AuditLogRepository auditLogRepository,
                                  PasswordEncoder passwordEncoder,
                                  CredentialGeneratorService credentialGeneratorService) {
        this.userRepository = userRepository;
        this.parishRepository = parishRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.credentialGeneratorService = credentialGeneratorService;
    }

    @Transactional(readOnly = true)
    public Page<UserListItemDto> listUsers(Pageable pageable) {
        return userRepository.findAllOrderByFullNameIgnoreCase(pageable)
                .map(this::toUserListItemDto);
    }

    @Transactional(readOnly = true)
    public UserFormDto getUserForEdit(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return toUserFormDto(user);
    }

    @Transactional
    public CredentialsResult createUser(CreateUserFormDto dto) {
        validateForCreate(dto);

        String plainPassword = dto.getPassword();
        String plainRecoveryCode = credentialGeneratorService.generateRecoveryCode();

        User user = new User();
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setPhoneNumber(normalizePhone(dto.getPhoneNumber()));
        user.setRole(dto.getRole());
        user.setParish(resolveParish(dto.getRole(), dto.getParishId()));
        user.setActive(dto.isActive());
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        user.setRecoveryCodeHash(passwordEncoder.encode(plainRecoveryCode));

        userRepository.save(user);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("User created: " + user.getEmail() + " (role: " + user.getRole() + ")");
        auditLogRepository.save(auditLog);

        return new CredentialsResult(user.getFullName(), user.getEmail(), plainPassword, plainRecoveryCode);
    }

    @Transactional
    public void updateUser(Long id, UserFormDto dto, Long currentUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        validateForUpdate(dto, user);
        enforceLastAdminProtection(user, dto);

        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setPhoneNumber(normalizePhone(dto.getPhoneNumber()));
        user.setRole(dto.getRole());
        user.setParish(resolveParish(dto.getRole(), dto.getParishId()));
        user.setActive(dto.isActive());

        userRepository.save(user);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("User updated: " + user.getEmail());
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public CredentialsResult resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        String plainRecoveryCode = credentialGeneratorService.generateRecoveryCode();

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setRecoveryCodeHash(passwordEncoder.encode(plainRecoveryCode));

        userRepository.save(user);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Password reset for user: " + user.getEmail());
        auditLogRepository.save(auditLog);

        return new CredentialsResult(user.getFullName(), user.getEmail(), newPassword, plainRecoveryCode);
    }

    @Transactional
    public RecoveryCodeResult resetRecoveryCode(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        String plainRecoveryCode = credentialGeneratorService.generateRecoveryCode();

        user.setRecoveryCodeHash(passwordEncoder.encode(plainRecoveryCode));

        userRepository.save(user);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Recovery code reset for user: " + user.getEmail());
        auditLogRepository.save(auditLog);

        return new RecoveryCodeResult(user.getFullName(), user.getEmail(), plainRecoveryCode);
    }

    public boolean isEmailTaken(String email, Long excludeId) {
        if (excludeId == null) {
            return userRepository.existsByEmailIgnoreCase(email);
        }
        return userRepository.existsByEmailIgnoreCaseAndIdNot(email, excludeId);
    }

    // --- Private helpers ---

    private void validateForCreate(CreateUserFormDto dto) {
        Map<String, String> errors = new HashMap<>();

        if (userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            errors.put("email", "A user with this email already exists");
        }

        validateRoleParishInvariant(dto.getRole(), dto.getParishId(), errors);

        if (!errors.isEmpty()) {
            throw new UserValidationException(errors);
        }
    }

    private void validateForUpdate(UserFormDto dto, User existingUser) {
        Map<String, String> errors = new HashMap<>();

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(dto.getEmail(), existingUser.getId())) {
            errors.put("email", "A user with this email already exists");
        }

        validateRoleParishInvariant(dto.getRole(), dto.getParishId(), errors);

        if (!errors.isEmpty()) {
            throw new UserValidationException(errors);
        }
    }

    private void validateRoleParishInvariant(UserRole role, Long parishId, Map<String, String> errors) {
        if (role == UserRole.ADMINISTRATOR && parishId != null) {
            errors.put("parishId", "An administrator must not be assigned to a parish");
        }
        if (role == UserRole.PARISH_PRIEST) {
            if (parishId == null) {
                errors.put("parishId", "A parish priest must be assigned to a parish");
            } else if (!parishRepository.existsById(parishId)) {
                errors.put("parishId", "The selected parish does not exist");
            }
        }
    }

    private void enforceLastAdminProtection(User existingUser, UserFormDto dto) {
        if (existingUser.getRole() != UserRole.ADMINISTRATOR || !existingUser.isActive()) {
            return;
        }

        boolean beingDeactivated = !dto.isActive();
        boolean roleChanging = dto.getRole() != UserRole.ADMINISTRATOR;

        if (!beingDeactivated && !roleChanging) {
            return;
        }

        long activeAdminCount = userRepository.countActiveAdministrators();
        if (activeAdminCount <= 1) {
            throw new LastAdministratorException();
        }
    }

    private Parish resolveParish(UserRole role, Long parishId) {
        if (role == UserRole.ADMINISTRATOR) {
            return null;
        }
        return parishRepository.findById(parishId).orElse(null);
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone;
    }

    private UserListItemDto toUserListItemDto(User user) {
        String parishName = null;
        if (user.getParish() != null) {
            parishName = user.getParish().getLocality() + " - " + user.getParish().getName();
        }
        return new UserListItemDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                parishName,
                user.isActive()
        );
    }

    private UserFormDto toUserFormDto(User user) {
        UserFormDto dto = new UserFormDto();
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setRole(user.getRole());
        dto.setParishId(user.getParish() != null ? user.getParish().getId() : null);
        dto.setActive(user.isActive());
        return dto;
    }
}
