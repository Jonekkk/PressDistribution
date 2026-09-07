package com.example.pressdistribution.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Filter parameters for the Administrator parish report.
 * Parish selection is required; date range is optional.
 */
public class ParishReportFilterDto {

    @NotNull(message = "Parish is required")
    private Long parishId;

    private LocalDate dateFrom;
    private LocalDate dateTo;

    public Long getParishId() {
        return parishId;
    }

    public void setParishId(Long parishId) {
        this.parishId = parishId;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }
}
