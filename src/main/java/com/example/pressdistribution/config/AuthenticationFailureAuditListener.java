package com.example.pressdistribution.config;

import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.repository.AuditLogRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for Spring Security authentication failure events and records them in audit_logs.
 * Never logs passwords, recovery codes, secrets or session identifiers.
 */
@Component
public class AuthenticationFailureAuditListener {

    private final AuditLogRepository auditLogRepository;

    public AuthenticationFailureAuditListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @EventListener
    @Transactional
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String principal = event.getAuthentication().getName();
        // Truncate to prevent abuse via excessively long usernames
        if (principal != null && principal.length() > 254) {
            principal = principal.substring(0, 254);
        }

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Sign-in failure: " + principal);
        auditLogRepository.save(auditLog);
    }
}
