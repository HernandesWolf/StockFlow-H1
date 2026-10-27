package com.stockflow.exception;

/** Requisição válida, mas viola uma regra de negócio (HTTP 422). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
