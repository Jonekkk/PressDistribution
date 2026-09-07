package com.example.pressdistribution.controller;

import com.example.pressdistribution.dto.BulkEntryFormDto;
import com.example.pressdistribution.dto.BulkEntryRowDto;
import com.example.pressdistribution.dto.BulkEntryRowViewModel;
import com.example.pressdistribution.dto.BulkRowError;
import com.example.pressdistribution.dto.BulkValidationResult;
import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.ParishIssueRecord;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.service.BulkEntryService;
import com.example.pressdistribution.service.IssueService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/admin/records/bulk")
public class BulkRecordController {

    private final BulkEntryService bulkEntryService;
    private final IssueService issueService;
    private final ParishRepository parishRepository;
    private final IssueRepository issueRepository;
    private final ParishIssueRecordRepository parishIssueRecordRepository;

    public BulkRecordController(BulkEntryService bulkEntryService,
                                IssueService issueService,
                                ParishRepository parishRepository,
                                IssueRepository issueRepository,
                                ParishIssueRecordRepository parishIssueRecordRepository) {
        this.bulkEntryService = bulkEntryService;
        this.issueService = issueService;
        this.parishRepository = parishRepository;
        this.issueRepository = issueRepository;
        this.parishIssueRecordRepository = parishIssueRecordRepository;
    }

    @GetMapping
    public String showBulkEntry(@RequestParam(required = false) Long issueId, Model model) {
        model.addAttribute("issues", issueRepository.findAllSortedForList());

        if (issueId == null) {
            return "admin/records/bulk";
        }

        Optional<Issue> issueOpt = issueRepository.findByIdWithPublication(issueId);
        if (issueOpt.isEmpty()) {
            model.addAttribute("issueError", "Issue does not exist.");
            return "admin/records/bulk";
        }

        Issue issue = issueOpt.get();
        model.addAttribute("selectedIssue", issue);
        model.addAttribute("issueId", issueId);

        List<BulkEntryRowViewModel> rows = buildRowViewModels(issue);
        model.addAttribute("rows", rows);

        return "admin/records/bulk";
    }

    @PostMapping
    public String saveBulk(@ModelAttribute BulkEntryFormDto form,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        Long issueId = form.getIssueId();
        List<BulkEntryRowDto> rows = form.getRows();

        if (rows == null) {
            rows = new ArrayList<>();
        }

        BulkValidationResult result;
        try {
            result = bulkEntryService.saveBulk(issueId, rows);
        } catch (Exception e) {
            // Persistence exception after entering transactional method
            // Transaction rollback is handled by Spring's @Transactional
            return rebuildViewWithError(issueId, form, model,
                    "The operation could not be completed. Please try again.");
        }

        if (result.isSuccess()) {
            redirectAttributes.addFlashAttribute("successMessage", "Bulk entry saved successfully.");
            return "redirect:/admin/records/bulk";
        }

        // Validation failure - redisplay table with row errors
        return rebuildViewWithValidationErrors(issueId, form, result, model);
    }

    private String rebuildViewWithError(Long issueId, BulkEntryFormDto form, Model model, String errorMessage) {
        model.addAttribute("issues", issueRepository.findAllSortedForList());
        model.addAttribute("globalError", errorMessage);

        if (issueId != null) {
            Optional<Issue> issueOpt = issueRepository.findByIdWithPublication(issueId);
            if (issueOpt.isPresent()) {
                Issue issue = issueOpt.get();
                model.addAttribute("selectedIssue", issue);
                model.addAttribute("issueId", issueId);

                List<BulkEntryRowViewModel> rows = buildRowViewModelsFromSubmission(issue, form);
                model.addAttribute("rows", rows);
            }
        }

        return "admin/records/bulk";
    }

    private String rebuildViewWithValidationErrors(Long issueId, BulkEntryFormDto form,
                                                   BulkValidationResult result, Model model) {
        model.addAttribute("issues", issueRepository.findAllSortedForList());

        if (result.getGlobalError() != null) {
            model.addAttribute("globalError", result.getGlobalError());
        }

        if (issueId != null) {
            Optional<Issue> issueOpt = issueRepository.findByIdWithPublication(issueId);
            if (issueOpt.isPresent()) {
                Issue issue = issueOpt.get();
                model.addAttribute("selectedIssue", issue);
                model.addAttribute("issueId", issueId);

                List<BulkEntryRowViewModel> rows = buildRowViewModelsFromSubmission(issue, form);

                // Apply row errors
                if (result.getRowErrors() != null) {
                    for (BulkRowError error : result.getRowErrors()) {
                        int idx = error.getRowIndex();
                        if (idx >= 0 && idx < rows.size()) {
                            BulkEntryRowViewModel row = rows.get(idx);
                            switch (error.getField()) {
                                case "deliveredCopies" -> row.setDeliveredError(error.getMessage());
                                case "returnedCopies" -> row.setReturnedError(error.getMessage());
                                case "paidAmount" -> row.setPaidAmountError(error.getMessage());
                                case "parishId" -> row.setDeliveredError(error.getMessage());
                                default -> { }
                            }
                        }
                    }
                }

                model.addAttribute("rows", rows);
            }
        }

        return "admin/records/bulk";
    }

    private List<BulkEntryRowViewModel> buildRowViewModels(Issue issue) {
        List<Parish> parishes = parishRepository.findAllSortedByLocalityAndName();
        List<ParishIssueRecord> existingRecords = parishIssueRecordRepository.findByIssueIdWithParish(issue.getId());

        Map<Long, ParishIssueRecord> recordsByParishId = new HashMap<>();
        for (ParishIssueRecord record : existingRecords) {
            recordsByParishId.put(record.getParish().getId(), record);
        }

        List<BulkEntryRowViewModel> rows = new ArrayList<>();
        for (Parish parish : parishes) {
            BulkEntryRowViewModel vm = new BulkEntryRowViewModel();
            vm.setParishId(parish.getId());
            vm.setParishLocality(parish.getLocality());
            vm.setParishName(parish.getName());

            ParishIssueRecord existing = recordsByParishId.get(parish.getId());
            if (existing != null) {
                vm.setDeliveredCopies(existing.getDeliveredCopies());
                vm.setReturnedCopies(existing.getReturnedCopies());
                vm.setPaidAmount(existing.getPaidAmount());
                int sold = existing.getDeliveredCopies() - existing.getReturnedCopies();
                vm.setSoldCopies(sold);
                vm.setAmountDue(issue.getUnitPrice().multiply(BigDecimal.valueOf(sold)));
                vm.setExistingRecord(true);
            } else {
                vm.setSoldCopies(0);
                vm.setAmountDue(BigDecimal.ZERO);
                vm.setExistingRecord(false);
            }

            rows.add(vm);
        }

        return rows;
    }

    private List<BulkEntryRowViewModel> buildRowViewModelsFromSubmission(Issue issue, BulkEntryFormDto form) {
        List<Parish> parishes = parishRepository.findAllSortedByLocalityAndName();
        List<ParishIssueRecord> existingRecords = parishIssueRecordRepository.findByIssueIdWithParish(issue.getId());

        Map<Long, ParishIssueRecord> recordsByParishId = new HashMap<>();
        for (ParishIssueRecord record : existingRecords) {
            recordsByParishId.put(record.getParish().getId(), record);
        }

        // Build a map of submitted values by parishId
        Map<Long, BulkEntryRowDto> submittedByParishId = new HashMap<>();
        if (form.getRows() != null) {
            for (BulkEntryRowDto rowDto : form.getRows()) {
                if (rowDto.getParishId() != null) {
                    submittedByParishId.put(rowDto.getParishId(), rowDto);
                }
            }
        }

        List<BulkEntryRowViewModel> rows = new ArrayList<>();
        for (Parish parish : parishes) {
            BulkEntryRowViewModel vm = new BulkEntryRowViewModel();
            vm.setParishId(parish.getId());
            vm.setParishLocality(parish.getLocality());
            vm.setParishName(parish.getName());

            ParishIssueRecord existing = recordsByParishId.get(parish.getId());
            BulkEntryRowDto submitted = submittedByParishId.get(parish.getId());

            // Populate with submitted values if available, otherwise existing values
            if (submitted != null) {
                Integer delivered = parseIntSafe(submitted.getDeliveredCopies());
                Integer returned = parseIntSafe(submitted.getReturnedCopies());
                BigDecimal paid = parseDecimalSafe(submitted.getPaidAmount());

                vm.setDeliveredCopies(delivered);
                vm.setReturnedCopies(returned);
                vm.setPaidAmount(paid);

                int effectiveDelivered = delivered != null ? delivered : (existing != null ? existing.getDeliveredCopies() : 0);
                int effectiveReturned = returned != null ? returned : (existing != null ? existing.getReturnedCopies() : 0);
                int sold = effectiveDelivered - effectiveReturned;
                vm.setSoldCopies(sold);
                vm.setAmountDue(issue.getUnitPrice().multiply(BigDecimal.valueOf(sold)));
            } else if (existing != null) {
                vm.setDeliveredCopies(existing.getDeliveredCopies());
                vm.setReturnedCopies(existing.getReturnedCopies());
                vm.setPaidAmount(existing.getPaidAmount());
                int sold = existing.getDeliveredCopies() - existing.getReturnedCopies();
                vm.setSoldCopies(sold);
                vm.setAmountDue(issue.getUnitPrice().multiply(BigDecimal.valueOf(sold)));
            } else {
                vm.setSoldCopies(0);
                vm.setAmountDue(BigDecimal.ZERO);
            }

            vm.setExistingRecord(existing != null);
            rows.add(vm);
        }

        return rows;
    }

    private Integer parseIntSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseDecimalSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
