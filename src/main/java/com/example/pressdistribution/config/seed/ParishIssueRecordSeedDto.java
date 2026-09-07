package com.example.pressdistribution.config.seed;

import java.math.BigDecimal;

public record ParishIssueRecordSeedDto(String parishLocality, String parishName, String publicationName,
                                       String issueNumber, int deliveredCopies, int returnedCopies,
                                       BigDecimal paidAmount) {
}
