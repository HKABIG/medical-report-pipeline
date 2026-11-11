package com.application.backend.catalog;

public class CatalogException extends RuntimeException {
    public CatalogException(String message) {
        super(message);
    }

    public CatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
