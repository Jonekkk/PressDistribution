package com.example.pressdistribution.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 12;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotBlank handle null
        }

        List<String> violations = new ArrayList<>();

        if (value.length() < MIN_LENGTH) {
            violations.add("at least 12 characters");
        }
        if (!containsUppercase(value)) {
            violations.add("at least one uppercase letter");
        }
        if (!containsLowercase(value)) {
            violations.add("at least one lowercase letter");
        }
        if (!containsDigit(value)) {
            violations.add("at least one digit");
        }
        if (!containsSpecial(value)) {
            violations.add("at least one non-alphanumeric character");
        }

        if (violations.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        String message = "Password must contain: " + String.join(", ", violations);
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }

    private boolean containsUppercase(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isUpperCase(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLowercase(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLowerCase(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDigit(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSpecial(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                return true;
            }
        }
        return false;
    }
}
