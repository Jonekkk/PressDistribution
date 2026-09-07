package com.example.pressdistribution.dto;

/**
 * Holds a plaintext recovery code transiently until stored in the HTTP session
 * for one-time display to the administrator.
 */
public record RecoveryCodeResult(String fullName, String email, String recoveryCode) {
}
