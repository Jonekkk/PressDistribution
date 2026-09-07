package com.example.pressdistribution.controller;

import com.example.pressdistribution.dto.IssueFormDto;
import com.example.pressdistribution.exception.PublicationNotFoundException;
import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.UserRepository;
import com.example.pressdistribution.service.IssueService;
import com.example.pressdistribution.service.ParishIssueRecordService;
import com.example.pressdistribution.service.PublicationService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class SharedIssueController {

    private final IssueService issueService;
    private final PublicationService publicationService;
    private final UserRepository userRepository;
    private final ParishIssueRecordService recordService;

    public SharedIssueController(IssueService issueService,
                                 PublicationService publicationService,
                                 UserRepository userRepository,
                                 ParishIssueRecordService recordService) {
        this.issueService = issueService;
        this.publicationService = publicationService;
        this.userRepository = userRepository;
        this.recordService = recordService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/issues")
    public String list(@AuthenticationPrincipal UserDetails principal,
                       @RequestParam(required = false) Long publicationId,
                       Model model) {
        User user = userRepository.findByEmailWithParish(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User not found"));

        List<Issue> issues;
        if (publicationId != null) {
            if (!publicationService.existsById(publicationId)) {
                model.addAttribute("errorMessage", "The selected publication does not exist.");
                issues = issueService.findAllSorted();
            } else {
                issues = issueService.findAllByPublicationId(publicationId);
            }
        } else {
            issues = issueService.findAllSorted();
        }

        model.addAttribute("issues", issues);
        model.addAttribute("publications", publicationService.findAllSorted());
        model.addAttribute("selectedPublicationId", publicationId);
        model.addAttribute("isAdministrator", user.getRole() == UserRole.ADMINISTRATOR);

        // For Parish Priest, determine which issues already have a record for their parish
        if (user.getRole() == UserRole.PARISH_PRIEST && user.getParish() != null) {
            Long parishId = user.getParish().getId();
            Set<Long> issueIdsWithRecords = issues.stream()
                    .map(Issue::getId)
                    .filter(issueId -> recordService.existsByParishAndIssue(parishId, issueId))
                    .collect(Collectors.toSet());
            model.addAttribute("issueIdsWithRecords", issueIdsWithRecords);
        }

        return "issues/list";
    }

    @GetMapping("/issues/new")
    public String showCreateForm(@RequestParam(required = false) Long publicationId, Model model) {
        IssueFormDto dto = new IssueFormDto();
        dto.setPublicationDate(LocalDate.now());

        if (publicationId != null) {
            try {
                publicationService.findById(publicationId);
                dto.setPublicationId(publicationId);
                // Auto-fill unit price from most recent issue
                issueService.suggestPrice(publicationId).ifPresent(dto::setUnitPrice);
            } catch (PublicationNotFoundException e) {
                model.addAttribute("errorMessage", "The specified publication does not exist.");
            }
        }

        model.addAttribute("issueForm", dto);
        model.addAttribute("publications", publicationService.findAllSorted());
        return "issues/form";
    }

    @PostMapping("/issues")
    public String createIssue(@Valid @ModelAttribute("issueForm") IssueFormDto dto,
                              BindingResult result,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        model.addAttribute("publications", publicationService.findAllSorted());

        if (result.hasErrors()) {
            return "issues/form";
        }

        try {
            publicationService.findById(dto.getPublicationId());
        } catch (PublicationNotFoundException e) {
            result.rejectValue("publicationId", "invalid", "Selected publication does not exist");
            return "issues/form";
        }

        if (issueService.isDuplicateForCreate(dto.getPublicationId(), dto.getIssueNumber())) {
            result.rejectValue("issueNumber", "duplicate", "An issue with this number already exists for the selected publication");
            return "issues/form";
        }

        Issue saved;
        try {
            saved = issueService.createIssue(dto);
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", "The operation could not be completed. Please try again.");
            return "issues/form";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Issue created successfully. You can now create a record for this issue.");
        return "redirect:/records/new?issueId=" + saved.getId();
    }

    /**
     * Read-only JSON endpoint returning issue defaults for a given publication.
     * Used by the form's JavaScript to auto-fill unit price on publication change.
     * Returns {"unitPrice": "4.50"} or {"unitPrice": null} if no prior issue exists.
     */
    @GetMapping("/issues/defaults")
    @ResponseBody
    public ResponseEntity<Map<String, BigDecimal>> getIssueDefaults(@RequestParam Long publicationId) {
        try {
            publicationService.findById(publicationId);
        } catch (PublicationNotFoundException e) {
            return ResponseEntity.badRequest().build();
        }

        Optional<BigDecimal> price = issueService.suggestPrice(publicationId);
        Map<String, BigDecimal> result = new java.util.HashMap<>();
        result.put("unitPrice", price.orElse(null));
        return ResponseEntity.ok(result);
    }
}
