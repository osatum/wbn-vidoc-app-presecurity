package com.osatum.wbn.application.shared.exception;

public class ResourceAlreadyExistsException extends RuntimeException {
    private final String fieldName;

    public ResourceAlreadyExistsException(String message) {
        this("name", message);
    }

    public ResourceAlreadyExistsException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
