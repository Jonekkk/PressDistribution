package com.example.pressdistribution.service;

import com.example.pressdistribution.dto.BulkEntryRowDto;
import com.example.pressdistribution.dto.BulkRowError;
import com.example.pressdistribution.dto.BulkValidationResult;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.ParishIssueRecord;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import com.example.pressdistribution.repository.ParishRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BulkEntryService {

    private static final BigDecimal MAX_PAID_AMOUNT = new BigDecimal("99999999.99");

    private final ParishIssueRecordRepository parishIssueRecordRepository;
    private final ParishRepository parishRepository;
    private final IssueRepository issueRepository;
    private final AuditLogRepository auditLogRepository;

    public BulkEntryService(ParishIssueRecordRepository parishIssueRecordRepository,
                            ParishRepository parishRepository,
                            IssueRepository issueRepository,
                            AuditLogRepository auditLogRepository) {
        this.parishIssueRecordRepository = parishIssueRecordRepository;
        this.parishRepository = parishRepository;
        this.issueRepository = issueRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public BulkValidationResult saveBulk(Long issueId, List<BulkEntryRowDto> rows) {
        BulkValidationResult result = new BulkValidationResult();

        // Step 1: Validate Issue exists
        Issue issue = issueRepository.findByIdWithPublication(issueId).orElse(null);
        if (issue == null) {
            result.setSuccess(false);
            result.setGlobalError("Issue does not exist.");
            return result;
        }

        // Step 2: Validate row identities
        List<BulkRowError> identityErrors = validateRowIdentities(rows);
        if (!identityErrors.isEmpty()) {
            result.setSuccess(false);
            result.setRowErrors(identityErrors);
            return result;
        }

        // Load all parishes by ID for lookup
        Map<Long, Parish> parishMap = new HashMap<>();
        for (BulkEntryRowDto row : rows) {
            if (row.getParishId() != null) {
                parishRepository.findById(row.getParishId())
                        .ifPresent(p -> parishMap.put(p.getId(), p));
            }
        }

        // Load existing records for this issue
        List<ParishIssueRecord> existingRecords = parishIssueRecordRepository.findByIssueIdWithParish(issueId);
        Map<Long, ParishIssueRecord> existingByParishId = new HashMap<>();
        for (ParishIssueRecord record : existingRecords) {
            existingByParishId.put(record.getParish().getId(), record);
        }

        // Step 3: Detect changed rows
        List<ChangedRow> changedRows = detectChangedRows(rows, existingByParishId);

        // If no rows changed, return success with zero counts
        if (changedRows.isEmpty()) {
            result.setSuccess(true);
            result.setCreatedCount(0);
            result.setUpdatedCount(0);
            return result;
        }

        // Step 4: Validate all changed rows
        List<BulkRowError> validationErrors = validateChangedRows(changedRows, rows);
        if (!validationErrors.isEmpty()) {
            result.setSuccess(false);
            result.setRowErrors(validationErrors);
            return result;
        }

        // Step 5: Persist all changed rows + AuditLog entries
        int createdCount = 0;
        int updatedCount = 0;

        for (ChangedRow changed : changedRows) {
            Parish parish = parishMap.get(changed.parishId());
            ParishIssueRecord existing = existingByParishId.get(changed.parishId());

            if (existing != null) {
                // Update existing record
                existing.setDeliveredCopies(changed.deliveredCopies());
                existing.setReturnedCopies(changed.returnedCopies());
                existing.setPaidAmount(changed.paidAmount());
                parishIssueRecordRepository.save(existing);

                AuditLog auditLog = new AuditLog();
                auditLog.setMessage("Parish issue record updated: " +
                        parish.getLocality() + " " + parish.getName() + " - " +
                        issue.getPublication().getName() + " " + issue.getIssueNumber());
                auditLogRepository.save(auditLog);

                updatedCount++;
            } else {
                // Create new record
                ParishIssueRecord newRecord = new ParishIssueRecord();
                newRecord.setParish(parish);
                newRecord.setIssue(issue);
                newRecord.setDeliveredCopies(changed.deliveredCopies());
                newRecord.setReturnedCopies(changed.returnedCopies());
                newRecord.setPaidAmount(changed.paidAmount());
                parishIssueRecordRepository.save(newRecord);

                AuditLog auditLog = new AuditLog();
                auditLog.setMessage("Parish issue record created: " +
                        parish.getLocality() + " " + parish.getName() + " - " +
                        issue.getPublication().getName() + " " + issue.getIssueNumber());
                auditLogRepository.save(auditLog);

                createdCount++;
            }
        }

        result.setSuccess(true);
        result.setCreatedCount(createdCount);
        result.setUpdatedCount(updatedCount);
        return result;
    }

    private List<BulkRowError> validateRowIdentities(List<BulkEntryRowDto> rows) {
        List<BulkRowError> errors = new ArrayList<>();
        Set<Long> seenParishIds = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            BulkEntryRowDto row = rows.get(i);
            Long parishId = row.getParishId();

            if (parishId == null) {
                errors.add(new BulkRowError(i, "parishId", "Parish ID is required."));
                continue;
            }

            // Check for duplicates
            if (!seenParishIds.add(parishId)) {
                BulkValidationResult dupResult = new BulkValidationResult();
                dupResult.setGlobalError("Duplicate parish ID in request.");
                // Return a global error for duplicate parish IDs
                errors.add(new BulkRowError(i, "parishId", "Duplicate parish ID in request."));
                return errors;
            }

            // Check parish exists
            if (!parishRepository.existsById(parishId)) {
                errors.add(new BulkRowError(i, "parishId", "Parish does not exist."));
            }
        }

        return errors;
    }

    private List<ChangedRow> detectChangedRows(List<BulkEntryRowDto> rows,
                                                Map<Long, ParishIssueRecord> existingByParishId) {
        List<ChangedRow> changedRows = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            BulkEntryRowDto row = rows.get(i);
            Long parishId = row.getParishId();
            ParishIssueRecord existing = existingByParishId.get(parishId);

            if (existing == null) {
                // New row: changed if at least one field is non-blank
                if (isNonBlank(row.getDeliveredCopies()) ||
                    isNonBlank(row.getReturnedCopies()) ||
                    isNonBlank(row.getPaidAmount())) {

                    // For new rows, blank fields are treated as zero
                    int delivered = parseIntOrZero(row.getDeliveredCopies());
                    int returned = parseIntOrZero(row.getReturnedCopies());
                    BigDecimal paid = parseDecimalOrZero(row.getPaidAmount());

                    changedRows.add(new ChangedRow(i, parishId, delivered, returned, paid, true));
                }
            } else {
                // Existing row: changed if at least one non-blank field differs from persisted value
                boolean changed = false;

                int delivered = existing.getDeliveredCopies();
                int returned = existing.getReturnedCopies();
                BigDecimal paid = existing.getPaidAmount();

                if (isNonBlank(row.getDeliveredCopies())) {
                    Integer parsed = parseIntSafe(row.getDeliveredCopies());
                    if (parsed != null && parsed != existing.getDeliveredCopies()) {
                        changed = true;
                        delivered = parsed;
                    } else if (parsed != null) {
                        delivered = parsed;
                    } else {
                        // Non-blank but not parseable — will be caught in validation
                        changed = true;
                        delivered = existing.getDeliveredCopies();
                    }
                }

                if (isNonBlank(row.getReturnedCopies())) {
                    Integer parsed = parseIntSafe(row.getReturnedCopies());
                    if (parsed != null && parsed != existing.getReturnedCopies()) {
                        changed = true;
                        returned = parsed;
                    } else if (parsed != null) {
                        returned = parsed;
                    } else {
                        changed = true;
                        returned = existing.getReturnedCopies();
                    }
                }

                if (isNonBlank(row.getPaidAmount())) {
                    BigDecimal parsed = parseDecimalSafe(row.getPaidAmount());
                    if (parsed != null && parsed.compareTo(existing.getPaidAmount()) != 0) {
                        changed = true;
                        paid = parsed;
                    } else if (parsed != null) {
                        paid = parsed;
                    } else {
                        changed = true;
                        paid = existing.getPaidAmount();
                    }
                }

                if (changed) {
                    changedRows.add(new ChangedRow(i, parishId, delivered, returned, paid, false));
                }
            }
        }

        return changedRows;
    }

    private List<BulkRowError> validateChangedRows(List<ChangedRow> changedRows, List<BulkEntryRowDto> rows) {
        List<BulkRowError> errors = new ArrayList<>();

        for (ChangedRow changed : changedRows) {
            BulkEntryRowDto row = rows.get(changed.rowIndex());

            // Validate deliveredCopies
            String deliveredStr = row.getDeliveredCopies();
            Integer deliveredParsed = null;
            if (isNonBlank(deliveredStr)) {
                deliveredParsed = parseIntSafe(deliveredStr.trim());
                if (deliveredParsed == null) {
                    errors.add(new BulkRowError(changed.rowIndex(), "deliveredCopies",
                            "Delivered copies must be a valid integer."));
                } else if (deliveredParsed < 0) {
                    errors.add(new BulkRowError(changed.rowIndex(), "deliveredCopies",
                            "Delivered copies must not be negative."));
                    deliveredParsed = null;
                }
            } else if (changed.isNewRow()) {
                deliveredParsed = 0;
            }

            // Validate returnedCopies
            String returnedStr = row.getReturnedCopies();
            Integer returnedParsed = null;
            if (isNonBlank(returnedStr)) {
                returnedParsed = parseIntSafe(returnedStr.trim());
                if (returnedParsed == null) {
                    errors.add(new BulkRowError(changed.rowIndex(), "returnedCopies",
                            "Returned copies must be a valid integer."));
                } else if (returnedParsed < 0) {
                    errors.add(new BulkRowError(changed.rowIndex(), "returnedCopies",
                            "Returned copies must not be negative."));
                    returnedParsed = null;
                }
            } else if (changed.isNewRow()) {
                returnedParsed = 0;
            }

            // Validate paidAmount
            String paidStr = row.getPaidAmount();
            BigDecimal paidParsed = null;
            if (isNonBlank(paidStr)) {
                paidParsed = parseDecimalSafe(paidStr.trim());
                if (paidParsed == null) {
                    errors.add(new BulkRowError(changed.rowIndex(), "paidAmount",
                            "Paid amount must be a valid decimal number."));
                } else if (paidParsed.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(new BulkRowError(changed.rowIndex(), "paidAmount",
                            "Paid amount must not be negative."));
                    paidParsed = null;
                } else if (paidParsed.compareTo(MAX_PAID_AMOUNT) > 0) {
                    errors.add(new BulkRowError(changed.rowIndex(), "paidAmount",
                            "Paid amount must not exceed 99,999,999.99."));
                    paidParsed = null;
                } else if (paidParsed.scale() > 2) {
                    errors.add(new BulkRowError(changed.rowIndex(), "paidAmount",
                            "Paid amount must have at most 2 decimal places."));
                    paidParsed = null;
                }
            } else if (changed.isNewRow()) {
                paidParsed = BigDecimal.ZERO;
            }

            // Validate returned ≤ delivered (only if both are valid)
            int effectiveDelivered = changed.deliveredCopies();
            int effectiveReturned = changed.returnedCopies();

            // Use parsed values for validation comparison
            if (deliveredParsed != null) {
                effectiveDelivered = deliveredParsed;
            }
            if (returnedParsed != null) {
                effectiveReturned = returnedParsed;
            }

            if (deliveredParsed != null && returnedParsed != null && effectiveReturned > effectiveDelivered) {
                errors.add(new BulkRowError(changed.rowIndex(), "returnedCopies",
                        "Returned copies must not exceed delivered copies."));
            }
        }

        return errors;
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int parseIntOrZero(String value) {
        if (!isNonBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Integer parseIntSafe(String value) {
        if (!isNonBlank(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseDecimalOrZero(String value) {
        if (!isNonBlank(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal parseDecimalSafe(String value) {
        if (!isNonBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ChangedRow(int rowIndex, Long parishId, int deliveredCopies, int returnedCopies,
                              BigDecimal paidAmount, boolean isNewRow) {
    }
}
