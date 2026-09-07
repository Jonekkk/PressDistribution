package com.example.pressdistribution.service;

import com.example.pressdistribution.dto.AdminRecordFormDto;
import com.example.pressdistribution.dto.PriestRecordCreateFormDto;
import com.example.pressdistribution.dto.PriestRecordEditFormDto;
import com.example.pressdistribution.dto.RecordListItemDto;
import com.example.pressdistribution.dto.ReportRowDto;
import com.example.pressdistribution.exception.DuplicateRecordException;
import com.example.pressdistribution.exception.IssueNotFoundException;
import com.example.pressdistribution.exception.ParishNotFoundException;
import com.example.pressdistribution.exception.RecordNotFoundException;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.ParishIssueRecord;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import com.example.pressdistribution.repository.ParishRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ParishIssueRecordService {

    private final ParishIssueRecordRepository recordRepository;
    private final ParishRepository parishRepository;
    private final IssueRepository issueRepository;
    private final AuditLogRepository auditLogRepository;

    public ParishIssueRecordService(ParishIssueRecordRepository recordRepository,
                                    ParishRepository parishRepository,
                                    IssueRepository issueRepository,
                                    AuditLogRepository auditLogRepository) {
        this.recordRepository = recordRepository;
        this.parishRepository = parishRepository;
        this.issueRepository = issueRepository;
        this.auditLogRepository = auditLogRepository;
    }

    // ---- CREATE ----

    @Transactional
    public ParishIssueRecord createRecordAsAdmin(AdminRecordFormDto dto) {
        Parish parish = parishRepository.findById(dto.getParishId())
                .orElseThrow(() -> new ParishNotFoundException(dto.getParishId()));

        Issue issue = issueRepository.findById(dto.getIssueId())
                .orElseThrow(() -> new IssueNotFoundException(dto.getIssueId()));

        if (recordRepository.existsByParishIdAndIssueId(parish.getId(), issue.getId())) {
            throw new DuplicateRecordException(
                    "A record already exists for this parish and issue combination.");
        }

        validateReturnedNotExceedingDelivered(dto.getReturnedCopies(), dto.getDeliveredCopies());

        ParishIssueRecord record = new ParishIssueRecord();
        record.setParish(parish);
        record.setIssue(issue);
        record.setDeliveredCopies(dto.getDeliveredCopies());
        record.setReturnedCopies(dto.getReturnedCopies());
        record.setPaidAmount(dto.getPaidAmount());

        ParishIssueRecord saved = persistWithConcurrencyGuard(record);

        createAuditLog("Record created: " + formatRecordDescription(parish, issue));

        return saved;
    }

    @Transactional
    public ParishIssueRecord createRecordAsPriest(PriestRecordCreateFormDto dto, User currentUser) {
        Parish parish = currentUser.getParish();
        if (parish == null) {
            throw new ParishNotFoundException(0L);
        }

        Issue issue = issueRepository.findById(dto.getIssueId())
                .orElseThrow(() -> new IssueNotFoundException(dto.getIssueId()));

        if (recordRepository.existsByParishIdAndIssueId(parish.getId(), issue.getId())) {
            throw new DuplicateRecordException(
                    "A record already exists for this parish and issue combination.");
        }

        validateReturnedNotExceedingDelivered(dto.getReturnedCopies(), dto.getDeliveredCopies());

        ParishIssueRecord record = new ParishIssueRecord();
        record.setParish(parish);
        record.setIssue(issue);
        record.setDeliveredCopies(dto.getDeliveredCopies());
        record.setReturnedCopies(dto.getReturnedCopies());
        record.setPaidAmount(BigDecimal.ZERO.setScale(2));

        ParishIssueRecord saved = persistWithConcurrencyGuard(record);

        createAuditLog("Record created: " + formatRecordDescription(parish, issue));

        return saved;
    }

    // ---- UPDATE ----

    @Transactional
    public ParishIssueRecord updateRecordAsAdmin(Long id, AdminRecordFormDto dto) {
        ParishIssueRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException(id));

        Parish parish = parishRepository.findById(dto.getParishId())
                .orElseThrow(() -> new ParishNotFoundException(dto.getParishId()));

        Issue issue = issueRepository.findById(dto.getIssueId())
                .orElseThrow(() -> new IssueNotFoundException(dto.getIssueId()));

        // Check duplicate only if parish or issue changed
        if (!parish.getId().equals(record.getParish().getId())
                || !issue.getId().equals(record.getIssue().getId())) {
            if (recordRepository.existsByParishIdAndIssueIdAndIdNot(parish.getId(), issue.getId(), id)) {
                throw new DuplicateRecordException(
                        "A record already exists for this parish and issue combination.");
            }
        }

        validateReturnedNotExceedingDelivered(dto.getReturnedCopies(), dto.getDeliveredCopies());

        record.setParish(parish);
        record.setIssue(issue);
        record.setDeliveredCopies(dto.getDeliveredCopies());
        record.setReturnedCopies(dto.getReturnedCopies());
        record.setPaidAmount(dto.getPaidAmount());

        ParishIssueRecord updated = persistWithConcurrencyGuard(record);

        createAuditLog("Record updated: " + formatRecordDescription(parish, issue));

        return updated;
    }

    @Transactional
    public ParishIssueRecord updateRecordAsPriest(Long id, PriestRecordEditFormDto dto, User currentUser) {
        ParishIssueRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException(id));

        // Priest can only edit records belonging to their parish
        Parish priestParish = currentUser.getParish();
        if (priestParish == null || !priestParish.getId().equals(record.getParish().getId())) {
            throw new RecordNotFoundException(id);
        }

        validateReturnedNotExceedingDelivered(dto.getReturnedCopies(), dto.getDeliveredCopies());

        // Priest: only update delivered and returned copies; parish, issue, and paidAmount are preserved
        record.setDeliveredCopies(dto.getDeliveredCopies());
        record.setReturnedCopies(dto.getReturnedCopies());

        ParishIssueRecord updated = persistWithConcurrencyGuard(record);

        createAuditLog("Record updated: " + formatRecordDescription(record.getParish(), record.getIssue()));

        return updated;
    }

    // ---- FIND ----

    @Transactional(readOnly = true)
    public List<RecordListItemDto> findAllForAdmin() {
        return recordRepository.findAllWithDetailsOrdered()
                .stream()
                .map(RecordListItemDto::fromEntity)
                .toList();
    }

    /**
     * Returns records for a specific parish with paidAmount set to null.
     * This method is used for Parish Priest views, enforcing that paidAmount
     * is never exposed at the DTO level.
     */
    @Transactional(readOnly = true)
    public List<RecordListItemDto> findAllForParish(Long parishId) {
        return recordRepository.findByParishIdWithDetailsOrdered(parishId)
                .stream()
                .map(RecordListItemDto::fromEntity)
                .map(ParishIssueRecordService::stripPaidAmount)
                .toList();
    }

    /**
     * Returns paginated and filtered records for admin (all parishes).
     */
    @Transactional(readOnly = true)
    public Page<RecordListItemDto> findAllForAdminFiltered(Long publicationId, Long issueId, Pageable pageable) {
        return recordRepository.findAllFiltered(publicationId, issueId, pageable)
                .map(RecordListItemDto::fromEntity);
    }

    /**
     * Returns paginated and filtered records for a specific parish with paidAmount stripped.
     */
    @Transactional(readOnly = true)
    public Page<RecordListItemDto> findForParishFiltered(Long parishId, Long publicationId, Long issueId, Pageable pageable) {
        return recordRepository.findByParishIdFiltered(parishId, publicationId, issueId, pageable)
                .map(RecordListItemDto::fromEntity)
                .map(ParishIssueRecordService::stripPaidAmount);
    }

    @Transactional(readOnly = true)
    public ParishIssueRecord findById(Long id) {
        return recordRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RecordNotFoundException(id));
    }

    /**
     * Checks whether a record exists for the given parish and issue combination.
     */
    @Transactional(readOnly = true)
    public boolean existsByParishAndIssue(Long parishId, Long issueId) {
        return recordRepository.existsByParishIdAndIssueId(parishId, issueId);
    }

    // ---- PAID AMOUNT EXCLUSION (Task 3.4) ----

    /**
     * Strips paidAmount from a RecordListItemDto, returning a new instance with paidAmount = null.
     * Used for Parish Priest views to enforce data isolation at the DTO level.
     */
    public static RecordListItemDto stripPaidAmount(RecordListItemDto dto) {
        return new RecordListItemDto(
                dto.id(),
                dto.parishLocality(),
                dto.parishName(),
                dto.publicationName(),
                dto.issueNumber(),
                dto.issueDate(),
                dto.deliveredCopies(),
                dto.returnedCopies(),
                dto.soldCopies(),
                dto.unitPrice(),
                dto.amountDue(),
                null
        );
    }

    /**
     * Strips paidAmount from a list of ReportRowDto objects.
     * Used for Parish Priest report views to enforce data isolation at the DTO level.
     */
    public static List<ReportRowDto> stripPaidAmountFromReportRows(List<ReportRowDto> rows) {
        rows.forEach(row -> row.setPaidAmount(null));
        return rows;
    }

    /**
     * Strips paidAmount from a single ReportRowDto.
     */
    public static ReportRowDto stripPaidAmountFromReportRow(ReportRowDto row) {
        row.setPaidAmount(null);
        return row;
    }

    // ---- PRIVATE HELPERS ----

    private void validateReturnedNotExceedingDelivered(Integer returned, Integer delivered) {
        if (returned != null && delivered != null && returned > delivered) {
            throw new IllegalArgumentException("Returned copies must not exceed delivered copies.");
        }
    }

    private ParishIssueRecord persistWithConcurrencyGuard(ParishIssueRecord record) {
        try {
            return recordRepository.save(record);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateRecordException(
                    "A record already exists for this parish and issue combination.");
        }
    }

    private void createAuditLog(String message) {
        AuditLog auditLog = new AuditLog();
        auditLog.setMessage(truncateMessage(message));
        auditLogRepository.save(auditLog);
    }

    private String formatRecordDescription(Parish parish, Issue issue) {
        // Ensure the publication association is initialized
        String publicationName = issue.getPublication().getName();
        return parish.getLocality() + " - " + parish.getName()
                + ", " + publicationName + " #" + issue.getIssueNumber();
    }

    private String truncateMessage(String message) {
        if (message.length() > 1000) {
            return message.substring(0, 1000);
        }
        return message;
    }
}
