package com.example.pressdistribution.exception;

public class ParishNotFoundException extends RuntimeException {
    public ParishNotFoundException(Long id) {
        super("Parish not found with id: " + id);
    }
}
