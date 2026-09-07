package com.example.pressdistribution.dto;

import java.time.LocalDate;

/**
 * DTO for the publications list, including the latest issue date.
 */
public record PublicationListItemDto(
        Long id,
        String name,
        LocalDate latestIssueDate
) {
}
