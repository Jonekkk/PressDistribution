package com.example.pressdistribution.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class AdminRecordFormDto {

    @NotNull(message = "Parish is required")
    private Long parishId;

    @NotNull(message = "Issue is required")
    private Long issueId;

    @NotNull(message = "Delivered copies is required")
    @Min(value = 0, message = "Delivered copies must be at least 0")
    @Max(value = 2147483647, message = "Delivered copies exceeds maximum")
    private Integer deliveredCopies;

    @NotNull(message = "Returned copies is required")
    @Min(value = 0, message = "Returned copies must be at least 0")
    @Max(value = 2147483647, message = "Returned copies exceeds maximum")
    private Integer returnedCopies;

    @NotNull(message = "Paid amount is required")
    @DecimalMin(value = "0.00", message = "Paid amount must not be negative")
    @DecimalMax(value = "99999999.99", message = "Paid amount exceeds maximum")
    @Digits(integer = 8, fraction = 2, message = "Paid amount must have at most 2 decimal places")
    private BigDecimal paidAmount;

    public Long getParishId() {
        return parishId;
    }

    public void setParishId(Long parishId) {
        this.parishId = parishId;
    }

    public Long getIssueId() {
        return issueId;
    }

    public void setIssueId(Long issueId) {
        this.issueId = issueId;
    }

    public Integer getDeliveredCopies() {
        return deliveredCopies;
    }

    public void setDeliveredCopies(Integer deliveredCopies) {
        this.deliveredCopies = deliveredCopies;
    }

    public Integer getReturnedCopies() {
        return returnedCopies;
    }

    public void setReturnedCopies(Integer returnedCopies) {
        this.returnedCopies = returnedCopies;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }
}
