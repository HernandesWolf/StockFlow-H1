package com.stockflow.exception;

/**
 * A mesma Idempotency-Key foi reutilizada com um conteúdo diferente (HTTP 422).
 * Repetir a MESMA requisição é permitido (devolve a venda original);
 * mudar o conteúdo mantendo a chave é um erro do cliente.
 */
public class IdempotencyKeyReuseException extends BusinessRuleException {

    public IdempotencyKeyReuseException(String key) {
        super("A Idempotency-Key '" + key + "' já foi usada em outra requisição com conteúdo diferente.");
    }
}
