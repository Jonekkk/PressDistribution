package com.example.pressdistribution.config.seed;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IssueSeedDto(String publicationName, String issueNumber, LocalDate publicationDate,
                           BigDecimal unitPrice) {
}
