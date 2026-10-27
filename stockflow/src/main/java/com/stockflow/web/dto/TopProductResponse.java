package com.stockflow.web.dto;

import java.math.BigDecimal;

/** Linha do relatório "produtos mais vendidos". Construída direto pela consulta JPQL. */
public record TopProductResponse(
        Long productId,
        String sku,
        String name,
        Long quantitySold,
        BigDecimal revenue) {
}
