package com.example.pressdistribution.exception;

public class LastAdministratorException extends RuntimeException {
    public LastAdministratorException() {
        super("Cannot perform this operation: at least one active administrator must remain");
    }
}
