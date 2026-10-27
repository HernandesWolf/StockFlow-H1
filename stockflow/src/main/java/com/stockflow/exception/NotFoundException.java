package com.stockflow.exception;

/** Recurso não existe (HTTP 404). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " não encontrado(a): " + id);
    }
}
