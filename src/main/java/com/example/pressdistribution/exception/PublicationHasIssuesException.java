package com.example.pressdistribution.exception;

public class PublicationHasIssuesException extends RuntimeException {
    public PublicationHasIssuesException(Long publicationId) {
        super("Cannot delete publication with id: " + publicationId + " because it has existing issues");
    }
}
