package com.example.pressdistribution.controller;

import com.example.pressdistribution.dto.PasswordChangeFormDto;
import com.example.pressdistribution.dto.ProfileEditFormDto;
import com.example.pressdistribution.dto.ProfileViewDto;
import com.example.pressdistribution.dto.RecoveryCodeResetFormDto;
import com.example.pressdistribution.dto.RecoveryCodeUtil;
import com.example.pressdistribution.exception.InvalidCurrentPasswordException;
import com.example.pressdistribution.exception.UserValidationException;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.repository.UserRepository;
import com.example.pressdistribution.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.beans.PropertyEditorSupport;
import java.security.Principal;
import java.util.Locale;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private static final String SESSION_KEY_RECOVERY_CODE = "profileRecoveryCode";

    private final ProfileService profileService;
    private final UserRepository userRepository;

    public ProfileController(ProfileService profileService, UserRepository userRepository) {
        this.profileService = profileService;
        this.userRepository = userRepository;
    }

    @InitBinder("profileEditForm")
    public void initProfileEditBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, "fullName", new StringTrimmerEditor(true));
        binder.registerCustomEditor(String.class, "phoneNumber", new StringTrimmerEditor(true));
        binder.registerCustomEditor(String.class, "email", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text == null ? null : text.trim().toLowerCase(Locale.ROOT));
            }
        });
    }

    // GET /profile — read-only profile view
    @GetMapping
    public String viewProfile(Principal principal, Model model) {
        Long userId = getAuthenticatedUserId(principal);
        ProfileViewDto profileView = profileService.getProfileView(userId);
        model.addAttribute("profile", profileView);
        return "profile/view";
    }

    // GET /profile/edit — pre-populated edit form
    @GetMapping("/edit")
    public String showEditForm(Principal principal, Model model) {
        Long userId = getAuthenticatedUserId(principal);
        ProfileEditFormDto dto = profileService.getProfileForEdit(userId);
        model.addAttribute("profileEditForm", dto);
        addReadOnlyProfileAttributes(userId, model);
        return "profile/edit";
    }

    // POST /profile/edit — submit profile changes
    @PostMapping("/edit")
    public String updateProfile(@Valid @ModelAttribute("profileEditForm") ProfileEditFormDto dto,
                                BindingResult result,
                                Principal principal,
                                HttpServletRequest request,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        Long userId = getAuthenticatedUserId(principal);

        if (result.hasErrors()) {
            addReadOnlyProfileAttributes(userId, model);
            return "profile/edit";
        }

        try {
            boolean emailChanged = profileService.updateProfile(userId, dto);

            if (emailChanged) {
                request.getSession().invalidate();
                return "redirect:/login?profileUpdated";
            }

            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully");
            return "redirect:/profile";
        } catch (UserValidationException ex) {
            ex.getFieldErrors().forEach((field, message) ->
                    result.rejectValue(field, "validation", message));
            addReadOnlyProfileAttributes(userId, model);
            return "profile/edit";
        } catch (DataAccessException ex) {
            result.reject("globalError", "The operation could not be completed. Please try again.");
            addReadOnlyProfileAttributes(userId, model);
            return "profile/edit";
        }
    }

    // GET /profile/password — password change form
    @GetMapping("/password")
    public String showPasswordChangeForm(Model model) {
        model.addAttribute("passwordChangeForm", new PasswordChangeFormDto());
        return "profile/password";
    }

    // POST /profile/password — submit password change
    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute("passwordChangeForm") PasswordChangeFormDto dto,
                                 BindingResult result,
                                 Principal principal,
                                 HttpSession session,
                                 Model model) {
        if (result.hasErrors()) {
            clearPasswordFields(dto);
            return "profile/password";
        }

        // Confirm password match check in controller
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "validation", "Passwords do not match");
            clearPasswordFields(dto);
            return "profile/password";
        }

        Long userId = getAuthenticatedUserId(principal);

        try {
            String recoveryCode = profileService.changePassword(userId, dto);
            session.setAttribute(SESSION_KEY_RECOVERY_CODE, RecoveryCodeUtil.formatForDisplay(recoveryCode));
            return "redirect:/profile/recovery-code-result";
        } catch (InvalidCurrentPasswordException ex) {
            model.addAttribute("currentPasswordError", "Current password is incorrect.");
            clearPasswordFields(dto);
            return "profile/password";
        } catch (UserValidationException ex) {
            ex.getFieldErrors().forEach((field, message) ->
                    result.rejectValue(field, "validation", message));
            clearPasswordFields(dto);
            return "profile/password";
        } catch (DataAccessException ex) {
            model.addAttribute("globalError", "The operation could not be completed. Please try again.");
            clearPasswordFields(dto);
            return "profile/password";
        }
    }

    private void clearPasswordFields(PasswordChangeFormDto dto) {
        dto.setCurrentPassword(null);
        dto.setNewPassword(null);
        dto.setConfirmPassword(null);
    }

    // GET /profile/recovery-code-reset — confirmation page
    @GetMapping("/recovery-code-reset")
    public String showRecoveryCodeResetForm(Model model) {
        model.addAttribute("recoveryCodeResetForm", new RecoveryCodeResetFormDto());
        return "profile/recovery-code-reset";
    }

    // POST /profile/recovery-code-reset — submit recovery code reset
    @PostMapping("/recovery-code-reset")
    public String resetRecoveryCode(@Valid @ModelAttribute("recoveryCodeResetForm") RecoveryCodeResetFormDto dto,
                                    BindingResult result,
                                    Principal principal,
                                    HttpSession session,
                                    Model model) {
        if (result.hasErrors()) {
            model.addAttribute("recoveryCodeResetForm", new RecoveryCodeResetFormDto());
            return "profile/recovery-code-reset";
        }

        Long userId = getAuthenticatedUserId(principal);

        try {
            String recoveryCode = profileService.resetRecoveryCode(userId, dto.getCurrentPassword());
            session.setAttribute(SESSION_KEY_RECOVERY_CODE, RecoveryCodeUtil.formatForDisplay(recoveryCode));
            return "redirect:/profile/recovery-code-result";
        } catch (InvalidCurrentPasswordException ex) {
            model.addAttribute("currentPasswordError", "Current password is incorrect.");
            model.addAttribute("recoveryCodeResetForm", new RecoveryCodeResetFormDto());
            return "profile/recovery-code-reset";
        } catch (DataAccessException ex) {
            model.addAttribute("globalError", "The operation could not be completed. Please try again.");
            model.addAttribute("recoveryCodeResetForm", new RecoveryCodeResetFormDto());
            return "profile/recovery-code-reset";
        }
    }

    // GET /profile/recovery-code-result — one-time code display
    @GetMapping("/recovery-code-result")
    public String showRecoveryCodeResult(HttpSession session, Model model, HttpServletResponse response) {
        String recoveryCode = (String) session.getAttribute(SESSION_KEY_RECOVERY_CODE);

        if (recoveryCode == null) {
            return "redirect:/profile";
        }

        session.removeAttribute(SESSION_KEY_RECOVERY_CODE);

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");

        model.addAttribute("recoveryCode", recoveryCode);
        return "profile/recovery-code-result";
    }

    private Long getAuthenticatedUserId(Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + principal.getName()));
        return user.getId();
    }

    private void addReadOnlyProfileAttributes(Long userId, Model model) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        String roleName = user.getRole() == com.example.pressdistribution.model.UserRole.ADMINISTRATOR
                ? "Administrator" : "Parish Priest";

        com.example.pressdistribution.model.Parish parish = user.getParish();
        String parishDisplay = parish != null
                ? parish.getLocality() + " \u2013 " + parish.getName()
                : null;

        model.addAttribute("roleName", roleName);
        model.addAttribute("parishDisplay", parishDisplay);
        model.addAttribute("isActive", user.isActive());
    }
}
