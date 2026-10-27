package com.stockflow.web.dto;

import com.stockflow.domain.PriceHistory;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceHistoryResponse(
        Long id,
        BigDecimal oldCostPrice,
        BigDecimal newCostPrice,
        BigDecimal oldSalePrice,
        BigDecimal newSalePrice,
        Instant changedAt,
        String changedBy) {

    public static PriceHistoryResponse from(PriceHistory h) {
        return new PriceHistoryResponse(
                h.getId(),
                h.getOldCostPrice(),
                h.getNewCostPrice(),
                h.getOldSalePrice(),
                h.getNewSalePrice(),
                h.getChangedAt(),
                h.getChangedBy());
    }
}
