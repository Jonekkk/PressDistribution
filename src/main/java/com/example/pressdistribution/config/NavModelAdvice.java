package com.example.pressdistribution.config;

import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

/**
 * Provides role-related model attributes for the navigation bar fragment.
 */
@ControllerAdvice
public class NavModelAdvice {

    private final UserRepository userRepository;

    public NavModelAdvice(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute("navIsAdministrator")
    public boolean isAdministrator() {
        return hasRole(UserRole.ADMINISTRATOR);
    }

    @ModelAttribute("navIsParishPriest")
    public boolean isParishPriest() {
        return hasRole(UserRole.PARISH_PRIEST);
    }

    private boolean hasRole(UserRole role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserDetails)) {
            return false;
        }
        UserDetails principal = (UserDetails) auth.getPrincipal();
        Optional<User> user = userRepository.findByEmail(principal.getUsername());
        return user.isPresent() && user.get().getRole() == role;
    }
}
