package com.example.pressdistribution.controller;

import com.example.pressdistribution.dto.AdminRecordFormDto;
import com.example.pressdistribution.dto.PriestRecordCreateFormDto;
import com.example.pressdistribution.dto.PriestRecordEditFormDto;
import com.example.pressdistribution.dto.RecordListItemDto;
import com.example.pressdistribution.exception.DuplicateRecordException;
import com.example.pressdistribution.exception.RecordNotFoundException;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.ParishIssueRecord;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import com.example.pressdistribution.repository.UserRepository;
import com.example.pressdistribution.service.ParishIssueRecordService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/records")
public class RecordController {

    private static final int PAGE_SIZE = 20;

    private final ParishIssueRecordService recordService;
    private final ParishRepository parishRepository;
    private final IssueRepository issueRepository;
    private final PublicationRepository publicationRepository;
    private final UserRepository userRepository;

    public RecordController(ParishIssueRecordService recordService,
                            ParishRepository parishRepository,
                            IssueRepository issueRepository,
                            PublicationRepository publicationRepository,
                            UserRepository userRepository) {
        this.recordService = recordService;
        this.parishRepository = parishRepository;
        this.issueRepository = issueRepository;
        this.publicationRepository = publicationRepository;
        this.userRepository = userRepository;
    }

    // ---- LIST (Task 6.1) ----

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails principal,
                       @RequestParam(required = false) Long publicationId,
                       @RequestParam(required = false) Long issueId,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        User user = resolveUser(principal);

        // Validate filter parameters
        Long validatedPublicationId = validatePublicationId(publicationId);
        Long validatedIssueId = validateIssueId(issueId, validatedPublicationId);

        // Populate filter dropdowns
        model.addAttribute("publications", publicationRepository.findAllSortedByName());
        model.addAttribute("selectedPublicationId", validatedPublicationId);
        model.addAttribute("selectedIssueId", validatedIssueId);
        if (validatedPublicationId != null) {
            model.addAttribute("issues", issueRepository.findAllByPublicationIdSorted(validatedPublicationId));
        } else {
            model.addAttribute("issues", List.of());
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), PAGE_SIZE,
                Sort.by(Sort.Order.asc("issue.publication.name").ignoreCase(),
                        Sort.Order.desc("issue.publicationDate"),
                        Sort.Order.asc("issue.issueNumber").ignoreCase()));

        if (isAdministrator(user)) {
            Page<RecordListItemDto> records = recordService.findAllForAdminFiltered(
                    validatedPublicationId, validatedIssueId, pageable);
            model.addAttribute("records", records);
            model.addAttribute("isAdministrator", true);
            model.addAttribute("showPaidAmount", true);
        } else {
            Parish parish = requireParish(user);
            Page<RecordListItemDto> records = recordService.findForParishFiltered(
                    parish.getId(), validatedPublicationId, validatedIssueId, pageable);
            model.addAttribute("records", records);
            model.addAttribute("isAdministrator", false);
            model.addAttribute("showPaidAmount", false);
        }

        return "records/list";
    }

    // ---- FILTER API: Issues by Publication ----

    @GetMapping("/issues-by-publication")
    public String issuesByPublication(@RequestParam Long publicationId, Model model) {
        if (publicationId != null && publicationRepository.existsById(publicationId)) {
            model.addAttribute("issues", issueRepository.findAllByPublicationIdSorted(publicationId));
        } else {
            model.addAttribute("issues", List.of());
        }
        return "records/fragments :: issueOptions";
    }

    // ---- CREATE FORM (Task 6.2) ----

    @GetMapping("/new")
    public String showCreateForm(@AuthenticationPrincipal UserDetails principal,
                                 @RequestParam(required = false) Long issueId,
                                 Model model) {
        User user = resolveUser(principal);

        if (isAdministrator(user)) {
            AdminRecordFormDto dto = new AdminRecordFormDto();
            dto.setReturnedCopies(0);
            dto.setPaidAmount(java.math.BigDecimal.ZERO);
            if (issueId != null) {
                if (issueRepository.existsById(issueId)) {
                    dto.setIssueId(issueId);
                } else {
                    model.addAttribute("issueIdError", "The specified issue does not exist.");
                }
            }
            model.addAttribute("recordForm", dto);
            model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());
            model.addAttribute("issues", issueRepository.findAllSortedForList());
            model.addAttribute("isEdit", false);
            return "records/admin-form";
        } else {
            Parish parish = requireParish(user);
            PriestRecordCreateFormDto dto = new PriestRecordCreateFormDto();
            dto.setReturnedCopies(0);
            if (issueId != null) {
                if (issueRepository.existsById(issueId)) {
                    dto.setIssueId(issueId);
                } else {
                    model.addAttribute("issueIdError", "The specified issue does not exist.");
                }
            }
            model.addAttribute("recordForm", dto);
            populatePriestCreateFormModel(model, parish);
            return "records/priest-form";
        }
    }

    // ---- CREATE SUBMISSION (Task 6.2) ----

    @PostMapping("/new")
    public String createRecord(@AuthenticationPrincipal UserDetails principal,
                               @Valid AdminRecordFormDto adminDto,
                               BindingResult adminResult,
                               @Valid PriestRecordCreateFormDto priestDto,
                               BindingResult priestResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        User user = resolveUser(principal);

        if (isAdministrator(user)) {
            return handleAdminCreate(adminDto, adminResult, model, redirectAttributes);
        } else {
            Parish parish = requireParish(user);
            return handlePriestCreate(priestDto, priestResult, user, parish, model, redirectAttributes);
        }
    }

    private String handleAdminCreate(AdminRecordFormDto dto,
                                     BindingResult result,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populateAdminFormModel(model, false, null);
            return "records/admin-form";
        }

        try {
            recordService.createRecordAsAdmin(dto);
        } catch (DuplicateRecordException e) {
            result.reject("duplicate", e.getMessage());
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populateAdminFormModel(model, false, null);
            return "records/admin-form";
        } catch (IllegalArgumentException e) {
            result.rejectValue("returnedCopies", "invalid", e.getMessage());
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populateAdminFormModel(model, false, null);
            return "records/admin-form";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Record created successfully");
        return "redirect:/records";
    }

    private String handlePriestCreate(PriestRecordCreateFormDto dto,
                                      BindingResult result,
                                      User user,
                                      Parish parish,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populatePriestCreateFormModel(model, parish);
            return "records/priest-form";
        }

        try {
            recordService.createRecordAsPriest(dto, user);
        } catch (DuplicateRecordException e) {
            result.reject("duplicate", e.getMessage());
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populatePriestCreateFormModel(model, parish);
            return "records/priest-form";
        } catch (IllegalArgumentException e) {
            result.rejectValue("returnedCopies", "invalid", e.getMessage());
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populatePriestCreateFormModel(model, parish);
            return "records/priest-form";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Record created successfully");
        return "redirect:/records";
    }

    // ---- EDIT FORM (Task 6.3) ----

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetails principal,
                               Model model) {
        User user = resolveUser(principal);
        ParishIssueRecord record = findRecordOrThrow(id);

        if (isAdministrator(user)) {
            AdminRecordFormDto dto = new AdminRecordFormDto();
            dto.setParishId(record.getParish().getId());
            dto.setIssueId(record.getIssue().getId());
            dto.setDeliveredCopies(record.getDeliveredCopies());
            dto.setReturnedCopies(record.getReturnedCopies());
            dto.setPaidAmount(record.getPaidAmount());

            model.addAttribute("recordForm", dto);
            populateAdminFormModel(model, true, id);
            return "records/admin-form";
        } else {
            Parish parish = requireParish(user);
            verifyRecordOwnership(record, parish);

            PriestRecordEditFormDto dto = new PriestRecordEditFormDto();
            dto.setDeliveredCopies(record.getDeliveredCopies());
            dto.setReturnedCopies(record.getReturnedCopies());

            model.addAttribute("recordForm", dto);
            model.addAttribute("parishName", parish.getName());
            model.addAttribute("parishLocality", parish.getLocality());
            model.addAttribute("issues", issueRepository.findAllSortedForList());
            model.addAttribute("isEdit", true);
            model.addAttribute("recordId", id);
            model.addAttribute("issueName", record.getIssue().getPublication().getName()
                    + " #" + record.getIssue().getIssueNumber());
            return "records/priest-form";
        }
    }

    // ---- EDIT SUBMISSION (Task 6.3) ----

    @PostMapping("/{id}/edit")
    public String updateRecord(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetails principal,
                               @Valid AdminRecordFormDto adminDto,
                               BindingResult adminResult,
                               @Valid PriestRecordEditFormDto priestDto,
                               BindingResult priestResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        User user = resolveUser(principal);

        if (isAdministrator(user)) {
            return handleAdminUpdate(id, adminDto, adminResult, model, redirectAttributes);
        } else {
            Parish parish = requireParish(user);
            ParishIssueRecord record = findRecordOrThrow(id);
            verifyRecordOwnership(record, parish);
            return handlePriestUpdate(id, priestDto, priestResult, user, parish, record, model, redirectAttributes);
        }
    }

    private String handleAdminUpdate(Long id,
                                     AdminRecordFormDto dto,
                                     BindingResult result,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populateAdminFormModel(model, true, id);
            return "records/admin-form";
        }

        try {
            recordService.updateRecordAsAdmin(id, dto);
        } catch (RecordNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (DuplicateRecordException e) {
            result.reject("duplicate", e.getMessage());
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populateAdminFormModel(model, true, id);
            return "records/admin-form";
        } catch (IllegalArgumentException e) {
            result.rejectValue("returnedCopies", "invalid", e.getMessage());
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populateAdminFormModel(model, true, id);
            return "records/admin-form";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Record updated successfully");
        return "redirect:/records";
    }

    private String handlePriestUpdate(Long id,
                                      PriestRecordEditFormDto dto,
                                      BindingResult result,
                                      User user,
                                      Parish parish,
                                      ParishIssueRecord record,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populatePriestEditFormModel(model, parish, id, record);
            return "records/priest-form";
        }

        try {
            recordService.updateRecordAsPriest(id, dto, user);
        } catch (RecordNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (DuplicateRecordException e) {
            result.reject("duplicate", e.getMessage());
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populatePriestEditFormModel(model, parish, id, record);
            return "records/priest-form";
        } catch (IllegalArgumentException e) {
            result.rejectValue("returnedCopies", "invalid", e.getMessage());
            model.addAttribute("recordForm", dto);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "recordForm", result);
            populatePriestEditFormModel(model, parish, id, record);
            return "records/priest-form";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Record updated successfully");
        return "redirect:/records";
    }

    // ---- HELPER METHODS ----

    private User resolveUser(UserDetails principal) {
        return userRepository.findByEmailWithParish(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User not found"));
    }

    private boolean isAdministrator(User user) {
        return user.getRole() == UserRole.ADMINISTRATOR;
    }

    private Parish requireParish(User user) {
        Parish parish = user.getParish();
        if (parish == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No parish assigned");
        }
        return parish;
    }

    private ParishIssueRecord findRecordOrThrow(Long id) {
        try {
            return recordService.findById(id);
        } catch (RecordNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private void verifyRecordOwnership(ParishIssueRecord record, Parish parish) {
        if (!record.getParish().getId().equals(parish.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private void populateAdminFormModel(Model model, boolean isEdit, Long recordId) {
        model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());
        model.addAttribute("issues", issueRepository.findAllSortedForList());
        model.addAttribute("isEdit", isEdit);
        if (isEdit) {
            model.addAttribute("recordId", recordId);
        }
    }

    private void populatePriestCreateFormModel(Model model, Parish parish) {
        model.addAttribute("parishName", parish.getName());
        model.addAttribute("parishLocality", parish.getLocality());
        List<com.example.pressdistribution.model.Issue> allIssues = issueRepository.findAllSortedForList();
        List<com.example.pressdistribution.model.Issue> availableIssues = allIssues.stream()
                .filter(issue -> !recordService.existsByParishAndIssue(parish.getId(), issue.getId()))
                .toList();
        model.addAttribute("issues", availableIssues);
        model.addAttribute("noIssuesAvailable", availableIssues.isEmpty());
        model.addAttribute("isEdit", false);
    }

    private void populatePriestEditFormModel(Model model, Parish parish, Long recordId, ParishIssueRecord record) {
        model.addAttribute("parishName", parish.getName());
        model.addAttribute("parishLocality", parish.getLocality());
        model.addAttribute("issues", issueRepository.findAllSortedForList());
        model.addAttribute("isEdit", true);
        model.addAttribute("recordId", recordId);
        model.addAttribute("issueName", record.getIssue().getPublication().getName()
                + " #" + record.getIssue().getIssueNumber());
    }

    private Long validatePublicationId(Long publicationId) {
        if (publicationId == null) {
            return null;
        }
        if (publicationRepository.existsById(publicationId)) {
            return publicationId;
        }
        return null;
    }

    private Long validateIssueId(Long issueId, Long validatedPublicationId) {
        if (issueId == null || validatedPublicationId == null) {
            return null;
        }
        return issueRepository.findByIdWithPublication(issueId)
                .filter(issue -> issue.getPublication().getId().equals(validatedPublicationId))
                .map(issue -> issue.getId())
                .orElse(null);
    }
}
