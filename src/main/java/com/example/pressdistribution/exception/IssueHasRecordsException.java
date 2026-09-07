package com.example.pressdistribution.exception;

public class IssueHasRecordsException extends RuntimeException {
    public IssueHasRecordsException(Long issueId) {
        super("Cannot delete issue with id: " + issueId + " because it has existing parish records");
    }
}
