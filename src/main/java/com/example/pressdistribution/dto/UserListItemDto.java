package com.example.pressdistribution.dto;

import com.example.pressdistribution.model.UserRole;

/**
 * Read-only projection for the user list page.
 * Prevents accidental exposure of password/recovery hashes in templates.
 */
public record UserListItemDto(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        UserRole role,
        String parishName,
        boolean active
) {
}
