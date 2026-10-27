package com.stockflow.web.dto;

import com.stockflow.domain.Sale;
import com.stockflow.domain.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleResponse(
        Long id,
        SaleStatus status,
        BigDecimal total,
        Instant createdAt,
        String createdBy,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        List<SaleItemResponse> items) {

    public static SaleResponse from(Sale sale) {
        return new SaleResponse(
                sale.getId(),
                sale.getStatus(),
                sale.getTotal(),
                sale.getCreatedAt(),
                sale.getCreatedBy(),
                sale.getCancelledAt(),
                sale.getCancelledBy(),
                sale.getCancelReason(),
                sale.getItems().stream().map(SaleItemResponse::from).toList());
    }
}
