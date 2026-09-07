package com.example.pressdistribution.dto;

import java.math.BigDecimal;

public class ReportTotalsDto {

    private Long totalDelivered;
    private Long totalReturned;
    private Long totalSold;
    private BigDecimal totalAmountDue;
    private BigDecimal totalPaidAmount;

    public ReportTotalsDto() {
    }

    public ReportTotalsDto(Long totalDelivered, Long totalReturned, Long totalSold,
                           BigDecimal totalAmountDue, BigDecimal totalPaidAmount) {
        this.totalDelivered = totalDelivered;
        this.totalReturned = totalReturned;
        this.totalSold = totalSold;
        this.totalAmountDue = totalAmountDue;
        this.totalPaidAmount = totalPaidAmount;
    }

    public Long getTotalDelivered() {
        return totalDelivered;
    }

    public void setTotalDelivered(Long totalDelivered) {
        this.totalDelivered = totalDelivered;
    }

    public Long getTotalReturned() {
        return totalReturned;
    }

    public void setTotalReturned(Long totalReturned) {
        this.totalReturned = totalReturned;
    }

    public Long getTotalSold() {
        return totalSold;
    }

    public void setTotalSold(Long totalSold) {
        this.totalSold = totalSold;
    }

    public BigDecimal getTotalAmountDue() {
        return totalAmountDue;
    }

    public void setTotalAmountDue(BigDecimal totalAmountDue) {
        this.totalAmountDue = totalAmountDue;
    }

    public BigDecimal getTotalPaidAmount() {
        return totalPaidAmount;
    }

    public void setTotalPaidAmount(BigDecimal totalPaidAmount) {
        this.totalPaidAmount = totalPaidAmount;
    }
}
