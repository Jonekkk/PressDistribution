package com.example.pressdistribution.controller;

import com.example.pressdistribution.dto.PublicationFormDto;
import com.example.pressdistribution.exception.PublicationHasIssuesException;
import com.example.pressdistribution.exception.PublicationNotFoundException;
import com.example.pressdistribution.model.Publication;
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

@Controller
@RequestMapping("/admin/publications")
public class PublicationController {

    private final PublicationService publicationService;

    public PublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("publications", publicationService.findAllWithLatestIssueDate());
        return "admin/publications/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("publicationForm", new PublicationFormDto());
        model.addAttribute("editMode", false);
        return "admin/publications/form";
    }

    @PostMapping
    public String createPublication(@Valid @ModelAttribute("publicationForm") PublicationFormDto dto,
                                    BindingResult result,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        if (result.hasErrors()) {
            model.addAttribute("editMode", false);
            return "admin/publications/form";
        }

        if (publicationService.isDuplicateForCreate(dto.getName())) {
            result.rejectValue("name", "duplicate", "A publication with this name already exists");
            model.addAttribute("editMode", false);
            return "admin/publications/form";
        }

        publicationService.createPublication(dto);
        redirectAttributes.addFlashAttribute("successMessage", "Publication created successfully");
        return "redirect:/admin/publications";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        try {
            Publication publication = publicationService.findById(id);
            PublicationFormDto dto = new PublicationFormDto();
            dto.setName(publication.getName());
            model.addAttribute("publicationForm", dto);
            model.addAttribute("editMode", true);
            model.addAttribute("publicationId", id);
            return "admin/publications/form";
        } catch (PublicationNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{id}")
    public String updatePublication(@PathVariable Long id,
                                    @Valid @ModelAttribute("publicationForm") PublicationFormDto dto,
                                    BindingResult result,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        if (result.hasErrors()) {
            model.addAttribute("editMode", true);
            model.addAttribute("publicationId", id);
            return "admin/publications/form";
        }

        if (publicationService.isDuplicateForUpdate(id, dto.getName())) {
            result.rejectValue("name", "duplicate", "A publication with this name already exists");
            model.addAttribute("editMode", true);
            model.addAttribute("publicationId", id);
            return "admin/publications/form";
        }

        try {
            publicationService.updatePublication(id, dto);
        } catch (PublicationNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        redirectAttributes.addFlashAttribute("successMessage", "Publication updated successfully");
        return "redirect:/admin/publications";
    }

    @GetMapping("/{id}/delete")
    public String showDeleteConfirmation(@PathVariable Long id, Model model) {
        try {
            Publication publication = publicationService.findById(id);
            model.addAttribute("publication", publication);
            return "admin/publications/delete";
        } catch (PublicationNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{id}/delete")
    public String deletePublication(@PathVariable Long id,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        Publication publication;
        try {
            publication = publicationService.findById(id);
        } catch (PublicationNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        try {
            publicationService.deletePublication(id);
        } catch (PublicationHasIssuesException e) {
            model.addAttribute("publication", publication);
            model.addAttribute("errorMessage", "This publication cannot be deleted because it has existing issues.");
            return "admin/publications/delete";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Publication deleted successfully");
        return "redirect:/admin/publications";
    }
}
