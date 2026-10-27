package com.stockflow.web.dto;

import com.stockflow.domain.Sale;
import com.stockflow.domain.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Versão resumida (sem itens) usada na listagem de vendas. */
public record SaleSummaryResponse(
        Long id,
        SaleStatus status,
        BigDecimal total,
        Instant createdAt,
        String createdBy) {

    public static SaleSummaryResponse from(Sale sale) {
        return new SaleSummaryResponse(
                sale.getId(), sale.getStatus(), sale.getTotal(), sale.getCreatedAt(), sale.getCreatedBy());
    }
}
