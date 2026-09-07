package com.example.pressdistribution.controller;

import com.example.pressdistribution.dto.RecoveryCodeUtil;
import com.example.pressdistribution.dto.RecoveryLoginFormDto;
import com.example.pressdistribution.service.RecoveryLoginService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.beans.PropertyEditorSupport;
import java.util.Locale;
import java.util.Optional;

/**
 * Controller for the unauthenticated password recovery flow using a recovery code.
 */
@Controller
@RequestMapping("/login/recovery")
public class RecoveryLoginController {

    private static final String SESSION_KEY_RECOVERY_CODE = "recoveryLoginNewCode";

    private final RecoveryLoginService recoveryLoginService;

    public RecoveryLoginController(RecoveryLoginService recoveryLoginService) {
        this.recoveryLoginService = recoveryLoginService;
    }

    @InitBinder("recoveryLoginForm")
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, "email", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text == null ? null : text.trim().toLowerCase(Locale.ROOT));
            }
        });
    }

    @GetMapping
    public String showRecoveryForm(Model model) {
        model.addAttribute("recoveryLoginForm", new RecoveryLoginFormDto());
        return "login/recovery";
    }

    @PostMapping
    public String processRecovery(@Valid @ModelAttribute("recoveryLoginForm") RecoveryLoginFormDto dto,
                                  BindingResult result,
                                  HttpSession session,
                                  Model model) {
        if (result.hasErrors()) {
            clearSensitiveFields(dto);
            return "login/recovery";
        }

        // Confirm passwords match
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "validation", "Passwords do not match");
            clearSensitiveFields(dto);
            return "login/recovery";
        }

        Optional<String> newRecoveryCode = recoveryLoginService.resetPasswordWithRecoveryCode(
                dto.getEmail(), dto.getRecoveryCode(), dto.getNewPassword());

        if (newRecoveryCode.isEmpty()) {
            // Generic failure message — does not reveal whether email, code or account status is wrong
            model.addAttribute("recoveryError", "Unable to reset password. Please check your email and recovery code.");
            clearSensitiveFields(dto);
            return "login/recovery";
        }

        // Store formatted recovery code in session for one-time display
        session.setAttribute(SESSION_KEY_RECOVERY_CODE, RecoveryCodeUtil.formatForDisplay(newRecoveryCode.get()));
        return "redirect:/login/recovery/success";
    }

    @GetMapping("/success")
    public String showRecoverySuccess(HttpSession session, Model model, HttpServletResponse response) {
        String recoveryCode = (String) session.getAttribute(SESSION_KEY_RECOVERY_CODE);

        if (recoveryCode == null) {
            return "redirect:/login";
        }

        session.removeAttribute(SESSION_KEY_RECOVERY_CODE);

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");

        model.addAttribute("recoveryCode", recoveryCode);
        return "login/recovery-success";
    }

    private void clearSensitiveFields(RecoveryLoginFormDto dto) {
        dto.setNewPassword(null);
        dto.setConfirmPassword(null);
        dto.setRecoveryCode(null);
    }
}
