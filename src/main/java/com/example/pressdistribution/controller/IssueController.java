package com.example.pressdistribution.controller;

import com.example.pressdistribution.dto.IssueFormDto;
import com.example.pressdistribution.exception.IssueHasRecordsException;
import com.example.pressdistribution.exception.IssueNotFoundException;
import com.example.pressdistribution.exception.PublicationNotFoundException;
import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.service.IssueService;
import com.example.pressdistribution.service.PublicationService;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/issues")
public class IssueController {

    private final IssueService issueService;
    private final PublicationService publicationService;

    public IssueController(IssueService issueService, PublicationService publicationService) {
        this.issueService = issueService;
        this.publicationService = publicationService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long publicationId, Model model) {
        if (publicationId != null) {
            if (!publicationService.existsById(publicationId)) {
                model.addAttribute("errorMessage", "The selected publication does not exist.");
                model.addAttribute("issues", issueService.findAllSorted());
            } else {
                model.addAttribute("issues", issueService.findAllByPublicationId(publicationId));
            }
        } else {
            model.addAttribute("issues", issueService.findAllSorted());
        }
        model.addAttribute("publications", publicationService.findAllSorted());
        model.addAttribute("selectedPublicationId", publicationId);
        return "admin/issues/list";
    }

    @GetMapping("/new")
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
        model.addAttribute("editMode", false);
        model.addAttribute("publications", publicationService.findAllSorted());
        return "admin/issues/form";
    }

    @PostMapping
    public String createIssue(@Valid @ModelAttribute("issueForm") IssueFormDto dto,
                              BindingResult result,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        model.addAttribute("publications", publicationService.findAllSorted());

        if (result.hasErrors()) {
            model.addAttribute("editMode", false);
            return "admin/issues/form";
        }

        try {
            publicationService.findById(dto.getPublicationId());
        } catch (PublicationNotFoundException e) {
            result.rejectValue("publicationId", "invalid", "Selected publication does not exist");
            model.addAttribute("editMode", false);
            return "admin/issues/form";
        }

        if (issueService.isDuplicateForCreate(dto.getPublicationId(), dto.getIssueNumber())) {
            result.rejectValue("issueNumber", "duplicate", "An issue with this number already exists for the selected publication");
            model.addAttribute("editMode", false);
            return "admin/issues/form";
        }

        issueService.createIssue(dto);
        redirectAttributes.addFlashAttribute("successMessage", "Issue created successfully");
        return "redirect:/admin/issues";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        try {
            Issue issue = issueService.findById(id);
            IssueFormDto dto = new IssueFormDto();
            dto.setPublicationId(issue.getPublication().getId());
            dto.setIssueNumber(issue.getIssueNumber());
            dto.setPublicationDate(issue.getPublicationDate());
            dto.setUnitPrice(issue.getUnitPrice());
            model.addAttribute("issueForm", dto);
            model.addAttribute("editMode", true);
            model.addAttribute("issueId", id);
            model.addAttribute("publications", publicationService.findAllSorted());
            return "admin/issues/form";
        } catch (IssueNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{id}")
    public String updateIssue(@PathVariable Long id,
                              @Valid @ModelAttribute("issueForm") IssueFormDto dto,
                              BindingResult result,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        model.addAttribute("publications", publicationService.findAllSorted());

        if (result.hasErrors()) {
            model.addAttribute("editMode", true);
            model.addAttribute("issueId", id);
            return "admin/issues/form";
        }

        try {
            publicationService.findById(dto.getPublicationId());
        } catch (PublicationNotFoundException e) {
            result.rejectValue("publicationId", "invalid", "Selected publication does not exist");
            model.addAttribute("editMode", true);
            model.addAttribute("issueId", id);
            return "admin/issues/form";
        }

        if (issueService.isDuplicateForUpdate(id, dto.getPublicationId(), dto.getIssueNumber())) {
            result.rejectValue("issueNumber", "duplicate", "An issue with this number already exists for the selected publication");
            model.addAttribute("editMode", true);
            model.addAttribute("issueId", id);
            return "admin/issues/form";
        }

        try {
            issueService.updateIssue(id, dto);
        } catch (IssueNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        redirectAttributes.addFlashAttribute("successMessage", "Issue updated successfully");
        return "redirect:/admin/issues";
    }

    @GetMapping("/{id}/delete")
    public String showDeleteConfirmation(@PathVariable Long id, Model model) {
        try {
            Issue issue = issueService.findById(id);
            model.addAttribute("issue", issue);
            return "admin/issues/delete";
        } catch (IssueNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteIssue(@PathVariable Long id,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        Issue issue;
        try {
            issue = issueService.findById(id);
        } catch (IssueNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        try {
            issueService.deleteIssue(id);
        } catch (IssueHasRecordsException e) {
            model.addAttribute("issue", issue);
            model.addAttribute("errorMessage", "This issue cannot be deleted because it has existing parish records.");
            return "admin/issues/delete";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Issue deleted successfully");
        return "redirect:/admin/issues";
    }


}
