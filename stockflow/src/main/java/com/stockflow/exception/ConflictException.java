package com.stockflow.exception;

/** O estado atual do recurso impede a operação (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
