package com.example.pressdistribution.controller;

import com.example.pressdistribution.dto.CreateUserFormDto;
import com.example.pressdistribution.dto.CredentialsResult;
import com.example.pressdistribution.dto.PasswordFormDto;
import com.example.pressdistribution.dto.RecoveryCodeResult;
import com.example.pressdistribution.dto.RecoveryCodeUtil;
import com.example.pressdistribution.dto.UserFormDto;
import com.example.pressdistribution.exception.LastAdministratorException;
import com.example.pressdistribution.exception.UserNotFoundException;
import com.example.pressdistribution.exception.UserValidationException;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.repository.UserRepository;
import com.example.pressdistribution.service.CredentialGeneratorService;
import com.example.pressdistribution.service.UserManagementService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;
import java.beans.PropertyEditorSupport;
import java.security.Principal;
import java.util.Locale;

@Controller
@RequestMapping("/admin/users")
public class UserManagementController {

    private static final String SESSION_KEY_CREDENTIALS = "userCredentials";

    private final UserManagementService userManagementService;
    private final CredentialGeneratorService credentialGeneratorService;
    private final ParishRepository parishRepository;
    private final UserRepository userRepository;

    public UserManagementController(UserManagementService userManagementService,
                                     CredentialGeneratorService credentialGeneratorService,
                                     ParishRepository parishRepository,
                                     UserRepository userRepository) {
        this.userManagementService = userManagementService;
        this.credentialGeneratorService = credentialGeneratorService;
        this.parishRepository = parishRepository;
        this.userRepository = userRepository;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @InitBinder({"createUserForm", "userForm"})
    public void initEmailBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, "email", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text == null ? null : text.trim().toLowerCase(Locale.ROOT));
            }
        });
    }

    // 1. GET /admin/users — paginated list
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        var users = userManagementService.listUsers(PageRequest.of(page, 20));
        model.addAttribute("users", users);
        return "admin/users/list";
    }

    // 2. GET /admin/users/new — create form
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("createUserForm", new CreateUserFormDto());
        model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());
        model.addAttribute("editMode", false);
        return "admin/users/form";
    }

    // 3. POST /admin/users — create user
    @PostMapping
    public String createUser(@Valid @ModelAttribute("createUserForm") CreateUserFormDto dto,
                             BindingResult result,
                             Model model,
                             HttpSession session) {
        if (result.hasErrors()) {
            dto.setPassword(null);
            model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());
            model.addAttribute("editMode", false);
            return "admin/users/form";
        }

        try {
            CredentialsResult credentials = userManagementService.createUser(dto);
            session.setAttribute(SESSION_KEY_CREDENTIALS, credentials);
            return "redirect:/admin/users/credentials";
        } catch (UserValidationException ex) {
            dto.setPassword(null);
            ex.getFieldErrors().forEach((field, message) ->
                    result.rejectValue(field, "validation", message));
            model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());
            model.addAttribute("editMode", false);
            return "admin/users/form";
        }
    }

    // 4. POST /admin/users/generate-password — generate password for create form
    @PostMapping("/generate-password")
    public String generatePasswordForCreate(@ModelAttribute("createUserForm") CreateUserFormDto dto,
                                            Model model,
                                            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");

        String generatedPassword = credentialGeneratorService.generatePassword();
        dto.setPassword(generatedPassword);

        model.addAttribute("createUserForm", dto);
        model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());
        model.addAttribute("editMode", false);
        return "admin/users/form";
    }

    // 5. GET /admin/users/{id}/edit — edit form
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        try {
            UserFormDto dto = userManagementService.getUserForEdit(id);
            model.addAttribute("userForm", dto);
            model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());
            model.addAttribute("editMode", true);
            model.addAttribute("userId", id);
            return "admin/users/form";
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // 6. POST /admin/users/{id} — update user
    @PostMapping("/{id}")
    public String updateUser(@PathVariable Long id,
                             @Valid @ModelAttribute("userForm") UserFormDto dto,
                             BindingResult result,
                             Model model,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());
            model.addAttribute("editMode", true);
            model.addAttribute("userId", id);
            return "admin/users/form";
        }

        try {
            Long currentUserId = getCurrentUserId(principal);
            userManagementService.updateUser(id, dto, currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "User updated successfully");
            return "redirect:/admin/users";
        } catch (UserValidationException ex) {
            ex.getFieldErrors().forEach((field, message) ->
                    result.rejectValue(field, "validation", message));
            model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());
            model.addAttribute("editMode", true);
            model.addAttribute("userId", id);
            return "admin/users/form";
        } catch (LastAdministratorException ex) {
            model.addAttribute("globalError", ex.getMessage());
            model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());
            model.addAttribute("editMode", true);
            model.addAttribute("userId", id);
            return "admin/users/form";
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // 7. GET /admin/users/{id}/password-reset — password reset form
    @GetMapping("/{id}/password-reset")
    public String showPasswordResetForm(@PathVariable Long id, Model model) {
        try {
            UserFormDto user = userManagementService.getUserForEdit(id);
            model.addAttribute("passwordForm", new PasswordFormDto());
            model.addAttribute("targetUserName", user.getFullName());
            model.addAttribute("targetUserEmail", user.getEmail());
            model.addAttribute("userId", id);
            return "admin/users/password-reset";
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // 8. POST /admin/users/{id}/password-reset — reset password
    @PostMapping("/{id}/password-reset")
    public String resetPassword(@PathVariable Long id,
                                @Valid @ModelAttribute("passwordForm") PasswordFormDto dto,
                                BindingResult result,
                                Model model,
                                HttpSession session) {
        if (result.hasErrors()) {
            try {
                UserFormDto user = userManagementService.getUserForEdit(id);
                model.addAttribute("targetUserName", user.getFullName());
                model.addAttribute("targetUserEmail", user.getEmail());
                model.addAttribute("userId", id);
            } catch (UserNotFoundException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
            }
            return "admin/users/password-reset";
        }

        try {
            CredentialsResult credentials = userManagementService.resetPassword(id, dto.getPassword());
            session.setAttribute(SESSION_KEY_CREDENTIALS, credentials);
            return "redirect:/admin/users/credentials";
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // 9. POST /admin/users/{id}/password-reset/generate-password — generate password for reset form
    @PostMapping("/{id}/password-reset/generate-password")
    public String generatePasswordForReset(@PathVariable Long id,
                                           Model model,
                                           HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");

        try {
            UserFormDto user = userManagementService.getUserForEdit(id);
            String generatedPassword = credentialGeneratorService.generatePassword();

            PasswordFormDto passwordForm = new PasswordFormDto();
            passwordForm.setPassword(generatedPassword);

            model.addAttribute("passwordForm", passwordForm);
            model.addAttribute("targetUserName", user.getFullName());
            model.addAttribute("targetUserEmail", user.getEmail());
            model.addAttribute("userId", id);
            return "admin/users/password-reset";
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // 10. POST /admin/users/{id}/recovery-code-reset — reset recovery code
    @PostMapping("/{id}/recovery-code-reset")
    public String resetRecoveryCode(@PathVariable Long id, HttpSession session) {
        try {
            RecoveryCodeResult result = userManagementService.resetRecoveryCode(id);
            session.setAttribute(SESSION_KEY_CREDENTIALS, result);
            return "redirect:/admin/users/credentials";
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // 11. GET /admin/users/credentials — one-time credentials display
    @GetMapping("/credentials")
    public String showCredentials(HttpSession session, Model model, HttpServletResponse response) {
        Object credentials = session.getAttribute(SESSION_KEY_CREDENTIALS);

        if (credentials == null) {
            return "redirect:/admin/users";
        }

        session.removeAttribute(SESSION_KEY_CREDENTIALS);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");

        if (credentials instanceof CredentialsResult cred) {
            model.addAttribute("fullName", cred.fullName());
            model.addAttribute("email", cred.email());
            model.addAttribute("password", cred.password());
            model.addAttribute("recoveryCode", RecoveryCodeUtil.formatForDisplay(cred.recoveryCode()));
            model.addAttribute("showPassword", true);
        } else if (credentials instanceof RecoveryCodeResult cred) {
            model.addAttribute("fullName", cred.fullName());
            model.addAttribute("email", cred.email());
            model.addAttribute("recoveryCode", RecoveryCodeUtil.formatForDisplay(cred.recoveryCode()));
            model.addAttribute("showPassword", false);
        }

        return "admin/users/credentials";
    }

    private Long getCurrentUserId(Principal principal) {
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Current user not found"))
                .getId();
    }
}
