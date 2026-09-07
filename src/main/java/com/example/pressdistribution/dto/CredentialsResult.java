package com.example.pressdistribution.dto;

/**
 * Holds plaintext credentials transiently until stored in the HTTP session
 * for one-time display to the administrator.
 */
public record CredentialsResult(String fullName, String email, String password, String recoveryCode) {
}
