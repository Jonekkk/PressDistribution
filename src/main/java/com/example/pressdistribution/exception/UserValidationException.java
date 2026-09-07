package com.example.pressdistribution.exception;

import java.util.Map;

public class UserValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public UserValidationException(Map<String, String> fieldErrors) {
        super("User validation failed");
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
