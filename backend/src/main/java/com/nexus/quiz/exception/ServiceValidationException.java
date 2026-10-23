package com.nexus.quiz.exception;

import java.util.Map;

public class ServiceValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ServiceValidationException(String field, String message) {
        super("Validation failed");
        this.fieldErrors = Map.of(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
