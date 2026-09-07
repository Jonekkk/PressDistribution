package com.example.pressdistribution.dto;

import java.time.LocalDate;

/**
 * Filter parameters for the Parish Priest "My Parish" report.
 * Contains only date range filters. The parish ID is resolved
 * server-side from the authenticated user's entity and is never
 * accepted from request parameters.
 */
public class MyParishReportFilterDto {

    private LocalDate dateFrom;
    private LocalDate dateTo;

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
