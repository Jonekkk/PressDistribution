package com.example.pressdistribution.service;

import com.example.pressdistribution.dto.MyParishReportFilterDto;
import com.example.pressdistribution.dto.ParishReportFilterDto;
import com.example.pressdistribution.dto.PublicationReportFilterDto;
import com.example.pressdistribution.dto.ReportRowDto;
import com.example.pressdistribution.dto.ReportTotalsDto;
import com.example.pressdistribution.model.ParishIssueRecord;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ReportService {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final ParishIssueRecordRepository recordRepository;

    public ReportService(ParishIssueRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    // ---- Publication Report (Administrator) ----

    @Transactional(readOnly = true)
    public Page<ReportRowDto> getPublicationReport(PublicationReportFilterDto filter, Pageable pageable) {
        Pageable effectivePageable = applyPublicationReportSort(pageable);
        Page<ParishIssueRecord> page = recordRepository.findPublicationReport(
                filter.getDateFrom(),
                filter.getDateTo(),
                filter.getPublicationId(),
                filter.getIssueId(),
                filter.getParishId(),
                effectivePageable);
        return page.map(this::toReportRowDto);
    }

    @Transactional(readOnly = true)
    public ReportTotalsDto getPublicationReportTotals(PublicationReportFilterDto filter) {
        List<ParishIssueRecord> allRecords = recordRepository.findPublicationReportAll(
                filter.getDateFrom(),
                filter.getDateTo(),
                filter.getPublicationId(),
                filter.getIssueId(),
                filter.getParishId());
        return computeTotals(allRecords);
    }

    @Transactional(readOnly = true)
    public List<ReportRowDto> getPublicationReportAll(PublicationReportFilterDto filter) {
        List<ParishIssueRecord> allRecords = recordRepository.findPublicationReportAll(
                filter.getDateFrom(),
                filter.getDateTo(),
                filter.getPublicationId(),
                filter.getIssueId(),
                filter.getParishId());
        return allRecords.stream()
                .map(this::toReportRowDto)
                .toList();
    }

    // ---- Parish Report (Administrator) ----

    @Transactional(readOnly = true)
    public Page<ReportRowDto> getParishReport(ParishReportFilterDto filter, Pageable pageable) {
        Pageable effectivePageable = applyParishReportSort(pageable);
        Page<ParishIssueRecord> page = recordRepository.findParishReport(
                filter.getParishId(),
                filter.getDateFrom(),
                filter.getDateTo(),
                effectivePageable);
        return page.map(this::toReportRowDto);
    }

    @Transactional(readOnly = true)
    public ReportTotalsDto getParishReportTotals(ParishReportFilterDto filter) {
        List<ParishIssueRecord> allRecords = recordRepository.findParishReportAll(
                filter.getParishId(),
                filter.getDateFrom(),
                filter.getDateTo());
        return computeTotals(allRecords);
    }

    @Transactional(readOnly = true)
    public List<ReportRowDto> getParishReportAll(ParishReportFilterDto filter) {
        List<ParishIssueRecord> allRecords = recordRepository.findParishReportAll(
                filter.getParishId(),
                filter.getDateFrom(),
                filter.getDateTo());
        return allRecords.stream()
                .map(this::toReportRowDto)
                .toList();
    }

    // ---- My Parish Report (Parish Priest) ----

    @Transactional(readOnly = true)
    public Page<ReportRowDto> getMyParishReport(Long authenticatedParishId,
                                                 MyParishReportFilterDto filter,
                                                 Pageable pageable) {
        Pageable effectivePageable = applyParishReportSort(pageable);
        Page<ParishIssueRecord> page = recordRepository.findParishReport(
                authenticatedParishId,
                filter.getDateFrom(),
                filter.getDateTo(),
                effectivePageable);
        return page.map(this::toReportRowDto)
                .map(ParishIssueRecordService::stripPaidAmountFromReportRow);
    }

    @Transactional(readOnly = true)
    public ReportTotalsDto getMyParishReportTotals(Long authenticatedParishId,
                                                    MyParishReportFilterDto filter) {
        List<ParishIssueRecord> allRecords = recordRepository.findParishReportAll(
                authenticatedParishId,
                filter.getDateFrom(),
                filter.getDateTo());
        ReportTotalsDto totals = computeTotals(allRecords);
        totals.setTotalPaidAmount(null);
        return totals;
    }

    @Transactional(readOnly = true)
    public List<ReportRowDto> getMyParishReportAll(Long authenticatedParishId,
                                                    MyParishReportFilterDto filter) {
        List<ParishIssueRecord> allRecords = recordRepository.findParishReportAll(
                authenticatedParishId,
                filter.getDateFrom(),
                filter.getDateTo());
        List<ReportRowDto> rows = allRecords.stream()
                .map(this::toReportRowDto)
                .toList();
        return ParishIssueRecordService.stripPaidAmountFromReportRows(new java.util.ArrayList<>(rows));
    }

    // ---- Private Helpers ----

    private ReportRowDto toReportRowDto(ParishIssueRecord record) {
        ReportRowDto dto = new ReportRowDto();
        dto.setPublicationName(record.getIssue().getPublication().getName());
        dto.setIssueNumber(record.getIssue().getIssueNumber());
        dto.setIssueDate(record.getIssue().getPublicationDate());
        dto.setParishLocality(record.getParish().getLocality());
        dto.setParishName(record.getParish().getName());
        dto.setDeliveredCopies(record.getDeliveredCopies());
        dto.setReturnedCopies(record.getReturnedCopies());

        int soldCopies = record.getDeliveredCopies() - record.getReturnedCopies();
        dto.setSoldCopies(soldCopies);

        BigDecimal unitPrice = record.getIssue().getUnitPrice();
        dto.setUnitPrice(unitPrice);

        BigDecimal amountDue = unitPrice.multiply(BigDecimal.valueOf(soldCopies))
                .setScale(2, RoundingMode.HALF_UP);
        dto.setAmountDue(amountDue);

        dto.setPaidAmount(record.getPaidAmount());
        return dto;
    }

    private ReportTotalsDto computeTotals(List<ParishIssueRecord> records) {
        long totalDelivered = 0;
        long totalReturned = 0;
        long totalSold = 0;
        BigDecimal totalAmountDue = BigDecimal.ZERO;
        BigDecimal totalPaidAmount = BigDecimal.ZERO;

        for (ParishIssueRecord record : records) {
            int delivered = record.getDeliveredCopies();
            int returned = record.getReturnedCopies();
            int sold = delivered - returned;

            totalDelivered += delivered;
            totalReturned += returned;
            totalSold += sold;

            BigDecimal unitPrice = record.getIssue().getUnitPrice();
            BigDecimal amountDue = unitPrice.multiply(BigDecimal.valueOf(sold))
                    .setScale(2, RoundingMode.HALF_UP);
            totalAmountDue = totalAmountDue.add(amountDue);
            totalPaidAmount = totalPaidAmount.add(record.getPaidAmount());
        }

        return new ReportTotalsDto(
                totalDelivered,
                totalReturned,
                totalSold,
                totalAmountDue.setScale(2, RoundingMode.HALF_UP),
                totalPaidAmount.setScale(2, RoundingMode.HALF_UP));
    }

    private Pageable applyPublicationReportSort(Pageable pageable) {
        int pageSize = pageable.getPageSize() > 0 ? pageable.getPageSize() : DEFAULT_PAGE_SIZE;
        Sort sort = Sort.by(
                Sort.Order.asc("issue.publication.name").ignoreCase(),
                Sort.Order.desc("issue.publicationDate"),
                Sort.Order.asc("issue.issueNumber").ignoreCase(),
                Sort.Order.asc("parish.locality").ignoreCase(),
                Sort.Order.asc("parish.name").ignoreCase());
        return PageRequest.of(pageable.getPageNumber(), pageSize, sort);
    }

    private Pageable applyParishReportSort(Pageable pageable) {
        int pageSize = pageable.getPageSize() > 0 ? pageable.getPageSize() : DEFAULT_PAGE_SIZE;
        Sort sort = Sort.by(
                Sort.Order.desc("issue.publicationDate"),
                Sort.Order.asc("issue.publication.name").ignoreCase(),
                Sort.Order.asc("issue.issueNumber").ignoreCase());
        return PageRequest.of(pageable.getPageNumber(), pageSize, sort);
    }
}
