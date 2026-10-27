package com.stockflow.web.dto;

import com.stockflow.domain.Money;

import java.math.BigDecimal;

/** Valor estimado do estoque (apenas produtos ativos). */
public record StockValuationResponse(
        long totalUnits,
        BigDecimal totalCostValue,
        BigDecimal totalSaleValue,
        BigDecimal potentialGrossProfit) {

    /**
     * Construtor usado pela consulta JPQL (SUM devolve null quando não há linhas,
     * então tratamos null como zero aqui).
     */
    public StockValuationResponse(Long units, BigDecimal cost, BigDecimal sale) {
        this(
                units == null ? 0L : units,
                cost == null ? Money.zero() : Money.of(cost),
                sale == null ? Money.zero() : Money.of(sale),
                (sale == null ? Money.zero() : Money.of(sale)).subtract(cost == null ? Money.zero() : Money.of(cost)));
    }
}
