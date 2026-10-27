package com.stockflow.exception;

/** Requisição malformada ou parâmetro inválido (HTTP 400). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
