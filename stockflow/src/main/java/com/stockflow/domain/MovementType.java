package com.stockflow.domain;

public enum MovementType {
    /** Entrada de mercadoria (compra, devolução...). */
    IN,
    /** Saída que não é venda (perda, quebra, uso interno...). */
    OUT,
    /** Ajuste de inventário (contagem física diferente do sistema). */
    ADJUSTMENT,
    /** Saída causada por uma venda. */
    SALE,
    /** Reversão do estoque de uma venda cancelada. */
    SALE_CANCEL
}
