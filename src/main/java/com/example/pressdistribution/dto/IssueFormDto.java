package com.example.pressdistribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class IssueFormDto {

    @NotNull(message = "Publication is required")
    private Long publicationId;

    @NotBlank(message = "Issue number is required")
    @Size(max = 100, message = "Issue number must not exceed 100 characters")
    @Pattern(regexp = "^[A-Za-z0-9 /\\-]+$", message = "Issue number may only contain letters, digits, spaces, slashes and hyphens")
    private String issueNumber;

    @NotNull(message = "Publication date is required")
    private LocalDate publicationDate;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "Unit price must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Unit price must have at most 8 integer digits and 2 decimal places")
    private BigDecimal unitPrice;

    public Long getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(Long publicationId) {
        this.publicationId = publicationId;
    }

    public String getIssueNumber() {
        return issueNumber;
    }

    public void setIssueNumber(String issueNumber) {
        this.issueNumber = issueNumber;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(LocalDate publicationDate) {
        this.publicationDate = publicationDate;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}
