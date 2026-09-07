package com.example.pressdistribution.controller;

import com.example.pressdistribution.dto.ParishFormDto;
import com.example.pressdistribution.exception.ParishNotFoundException;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.service.ParishService;
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
@RequestMapping("/admin/parishes")
public class ParishController {

    private final ParishService parishService;

    public ParishController(ParishService parishService) {
        this.parishService = parishService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("parishes", parishService.findAllSorted());
        return "admin/parishes/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("parishForm", new ParishFormDto());
        model.addAttribute("editMode", false);
        return "admin/parishes/form";
    }

    @PostMapping
    public String createParish(@Valid @ModelAttribute("parishForm") ParishFormDto dto,
                               BindingResult result,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        if (result.hasErrors()) {
            model.addAttribute("editMode", false);
            return "admin/parishes/form";
        }

        if (parishService.isDuplicateForCreate(dto.getLocality(), dto.getName())) {
            result.rejectValue("name", "duplicate", "A parish with this locality and name already exists");
            model.addAttribute("editMode", false);
            return "admin/parishes/form";
        }

        parishService.createParish(dto);
        redirectAttributes.addFlashAttribute("successMessage", "Parish created successfully");
        return "redirect:/admin/parishes";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        try {
            Parish parish = parishService.findById(id);
            ParishFormDto dto = new ParishFormDto();
            dto.setLocality(parish.getLocality());
            dto.setName(parish.getName());
            dto.setAddress(parish.getAddress());
            model.addAttribute("parishForm", dto);
            model.addAttribute("editMode", true);
            model.addAttribute("parishId", id);
            return "admin/parishes/form";
        } catch (ParishNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{id}")
    public String updateParish(@PathVariable Long id,
                               @Valid @ModelAttribute("parishForm") ParishFormDto dto,
                               BindingResult result,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        if (result.hasErrors()) {
            model.addAttribute("editMode", true);
            model.addAttribute("parishId", id);
            return "admin/parishes/form";
        }

        if (parishService.isDuplicateForUpdate(id, dto.getLocality(), dto.getName())) {
            result.rejectValue("name", "duplicate", "A parish with this locality and name already exists");
            model.addAttribute("editMode", true);
            model.addAttribute("parishId", id);
            return "admin/parishes/form";
        }

        try {
            parishService.updateParish(id, dto);
        } catch (ParishNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        redirectAttributes.addFlashAttribute("successMessage", "Parish updated successfully");
        return "redirect:/admin/parishes";
    }
}
