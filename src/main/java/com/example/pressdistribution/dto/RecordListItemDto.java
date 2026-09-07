package com.example.pressdistribution.dto;

import com.example.pressdistribution.model.ParishIssueRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Read-only projection for the parish issue record list.
 * Sold copies and amount due are calculated, never persisted.
 */
public record RecordListItemDto(
        Long id,
        String parishLocality,
        String parishName,
        String publicationName,
        String issueNumber,
        LocalDate issueDate,
        Integer deliveredCopies,
        Integer returnedCopies,
        Integer soldCopies,
        BigDecimal unitPrice,
        BigDecimal amountDue,
        BigDecimal paidAmount
) {

    /**
     * Creates a RecordListItemDto from a ParishIssueRecord entity,
     * computing soldCopies and amountDue.
     * Requires that parish, issue, and issue.publication associations are initialized.
     */
    public static RecordListItemDto fromEntity(ParishIssueRecord record) {
        int delivered = record.getDeliveredCopies();
        int returned = record.getReturnedCopies();
        int sold = delivered - returned;

        BigDecimal unitPrice = record.getIssue().getUnitPrice();
        BigDecimal amountDue = BigDecimal.valueOf(sold)
                .multiply(unitPrice)
                .setScale(2, RoundingMode.HALF_UP);

        return new RecordListItemDto(
                record.getId(),
                record.getParish().getLocality(),
                record.getParish().getName(),
                record.getIssue().getPublication().getName(),
                record.getIssue().getIssueNumber(),
                record.getIssue().getPublicationDate(),
                delivered,
                returned,
                sold,
                unitPrice.setScale(2, RoundingMode.HALF_UP),
                amountDue,
                record.getPaidAmount() != null
                        ? record.getPaidAmount().setScale(2, RoundingMode.HALF_UP)
                        : null
        );
    }
}
