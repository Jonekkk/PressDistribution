package com.example.pressdistribution.dto;

/**
 * Immutable data carrier for the read-only profile display page.
 *
 * @param fullName      the user's full name
 * @param email         the user's email address
 * @param phoneNumber   the user's phone number (may be null)
 * @param roleName      display name of the role ("Administrator" or "Parish Priest")
 * @param parishDisplay formatted parish string ("Locality - Name") or null for Administrators
 */
public record ProfileViewDto(
        String fullName,
        String email,
        String phoneNumber,
        String roleName,
        String parishDisplay
) {
}
