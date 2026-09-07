package com.example.pressdistribution.controller;

import com.example.pressdistribution.dto.CsvExportUtil;
import com.example.pressdistribution.dto.MyParishReportFilterDto;
import com.example.pressdistribution.dto.ParishReportFilterDto;
import com.example.pressdistribution.dto.PublicationReportFilterDto;
import com.example.pressdistribution.dto.ReportRowDto;
import com.example.pressdistribution.dto.ReportTotalsDto;
import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import com.example.pressdistribution.repository.UserRepository;
import com.example.pressdistribution.service.ReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final ReportService reportService;
    private final ParishRepository parishRepository;
    private final PublicationRepository publicationRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;

    public ReportController(ReportService reportService,
                            ParishRepository parishRepository,
                            PublicationRepository publicationRepository,
                            IssueRepository issueRepository,
                            UserRepository userRepository) {
        this.reportService = reportService;
        this.parishRepository = parishRepository;
        this.publicationRepository = publicationRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
    }

    // ---- Publication Report (Task 7.1) ----

    @GetMapping("/publications")
    public String publicationReport(@ModelAttribute("filter") PublicationReportFilterDto filter,
                                    BindingResult bindingResult,
                                    @RequestParam(defaultValue = "0") int page,
                                    Model model) {
        populatePublicationReportDropdowns(model);

        if (!validatePublicationReportFilter(filter, bindingResult)) {
            model.addAttribute("rows", Page.empty());
            return "reports/publications";
        }

        Pageable pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE);
        Page<ReportRowDto> rows = reportService.getPublicationReport(filter, pageable);
        model.addAttribute("rows", rows);
        return "reports/publications";
    }

    @GetMapping("/publications/export")
    public String publicationReportExport(@ModelAttribute("filter") PublicationReportFilterDto filter,
                                          BindingResult bindingResult,
                                          HttpServletResponse response) throws IOException {
        if (!validatePublicationReportFilter(filter, bindingResult)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        List<ReportRowDto> rows = reportService.getPublicationReportAll(filter);
        CsvExportUtil.writePublicationReportCsv(rows, true, response);
        return null;
    }

    // ---- Parish Report (Task 7.2) ----

    @GetMapping("/parishes")
    public String parishReport(@ModelAttribute("filter") ParishReportFilterDto filter,
                               BindingResult bindingResult,
                               @RequestParam(defaultValue = "0") int page,
                               Model model) {
        model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());

        // If no parishId selected (first load), show form without data
        if (filter.getParishId() == null) {
            model.addAttribute("rows", Page.empty());
            model.addAttribute("totals", new ReportTotalsDto(0L, 0L, 0L,
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));
            model.addAttribute("showResults", false);
            return "reports/parishes";
        }

        if (!validateParishReportFilter(filter, bindingResult)) {
            model.addAttribute("rows", Page.empty());
            model.addAttribute("totals", new ReportTotalsDto(0L, 0L, 0L,
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));
            model.addAttribute("showResults", false);
            return "reports/parishes";
        }

        Pageable pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE);
        Page<ReportRowDto> rows = reportService.getParishReport(filter, pageable);
        ReportTotalsDto totals = reportService.getParishReportTotals(filter);
        model.addAttribute("rows", rows);
        model.addAttribute("totals", totals);
        model.addAttribute("showResults", true);
        return "reports/parishes";
    }

    @GetMapping("/parishes/export")
    public String parishReportExport(@ModelAttribute("filter") ParishReportFilterDto filter,
                                     BindingResult bindingResult,
                                     HttpServletResponse response) throws IOException {
        if (filter.getParishId() == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (!validateParishReportFilter(filter, bindingResult)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        List<ReportRowDto> rows = reportService.getParishReportAll(filter);
        ReportTotalsDto totals = reportService.getParishReportTotals(filter);
        CsvExportUtil.writeParishReportCsv(rows, totals, true, response);
        return null;
    }

    // ---- My Parish Report (Task 7.3) ----

    @GetMapping("/my-parish")
    public String myParishReport(@AuthenticationPrincipal UserDetails principal,
                                 @ModelAttribute("filter") MyParishReportFilterDto filter,
                                 BindingResult bindingResult,
                                 @RequestParam(defaultValue = "0") int page,
                                 Model model) {
        User user = resolveUser(principal);
        Parish parish = requireParish(user);

        if (!validateDateRange(filter.getDateFrom(), filter.getDateTo(), bindingResult)) {
            model.addAttribute("rows", Page.empty());
            model.addAttribute("totals", new ReportTotalsDto(0L, 0L, 0L,
                    java.math.BigDecimal.ZERO, null));
            model.addAttribute("parishName", parish.getLocality() + " - " + parish.getName());
            return "reports/my-parish";
        }

        Pageable pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE);
        Page<ReportRowDto> rows = reportService.getMyParishReport(parish.getId(), filter, pageable);
        ReportTotalsDto totals = reportService.getMyParishReportTotals(parish.getId(), filter);
        model.addAttribute("rows", rows);
        model.addAttribute("totals", totals);
        model.addAttribute("parishName", parish.getLocality() + " - " + parish.getName());
        return "reports/my-parish";
    }

    @GetMapping("/my-parish/export")
    public String myParishReportExport(@AuthenticationPrincipal UserDetails principal,
                                       @ModelAttribute("filter") MyParishReportFilterDto filter,
                                       BindingResult bindingResult,
                                       HttpServletResponse response) throws IOException {
        User user = resolveUser(principal);
        Parish parish = requireParish(user);

        if (!validateDateRange(filter.getDateFrom(), filter.getDateTo(), bindingResult)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        List<ReportRowDto> rows = reportService.getMyParishReportAll(parish.getId(), filter);
        ReportTotalsDto totals = reportService.getMyParishReportTotals(parish.getId(), filter);
        CsvExportUtil.writeParishReportCsv(rows, totals, false, response);
        return null;
    }

    // ---- Validation Helpers ----

    private boolean validatePublicationReportFilter(PublicationReportFilterDto filter,
                                                    BindingResult bindingResult) {
        boolean valid = true;

        // Validate date range
        if (!validateDateRange(filter.getDateFrom(), filter.getDateTo(), bindingResult)) {
            valid = false;
        }

        // Validate issue exists if provided
        if (filter.getIssueId() != null) {
            Optional<Issue> issueOpt = issueRepository.findByIdWithPublication(filter.getIssueId());
            if (issueOpt.isEmpty()) {
                bindingResult.rejectValue("issueId", "notFound",
                        "Selected issue does not exist");
                valid = false;
            } else if (filter.getPublicationId() != null) {
                // Validate issue belongs to publication
                Issue issue = issueOpt.get();
                if (!issue.getPublication().getId().equals(filter.getPublicationId())) {
                    bindingResult.rejectValue("issueId", "mismatch",
                            "Selected issue does not belong to the selected publication");
                    valid = false;
                }
            }
        }

        // Validate parish exists if provided
        if (filter.getParishId() != null) {
            if (!parishRepository.existsById(filter.getParishId())) {
                bindingResult.rejectValue("parishId", "notFound",
                        "Selected parish does not exist");
                valid = false;
            }
        }

        return valid;
    }

    private boolean validateParishReportFilter(ParishReportFilterDto filter,
                                               BindingResult bindingResult) {
        boolean valid = true;

        // Validate date range
        if (!validateDateRange(filter.getDateFrom(), filter.getDateTo(), bindingResult)) {
            valid = false;
        }

        // Validate parish exists
        if (filter.getParishId() != null && !parishRepository.existsById(filter.getParishId())) {
            bindingResult.rejectValue("parishId", "notFound",
                    "Selected parish does not exist");
            valid = false;
        }

        return valid;
    }

    private boolean validateDateRange(java.time.LocalDate dateFrom,
                                      java.time.LocalDate dateTo,
                                      BindingResult bindingResult) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            bindingResult.rejectValue("dateFrom", "invalid",
                    "Date from must not be later than date to");
            return false;
        }
        return true;
    }

    // ---- Dropdown Population ----

    private void populatePublicationReportDropdowns(Model model) {
        model.addAttribute("publications", publicationRepository.findAllSortedByName());
        model.addAttribute("issues", issueRepository.findAllSortedForList());
        model.addAttribute("parishes", parishRepository.findAllSortedByLocalityAndName());
    }

    // ---- User Resolution ----

    private User resolveUser(UserDetails principal) {
        return userRepository.findByEmailWithParish(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User not found"));
    }

    private Parish requireParish(User user) {
        if (user.getRole() != UserRole.PARISH_PRIEST) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        Parish parish = user.getParish();
        if (parish == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No parish assigned");
        }
        return parish;
    }
}
