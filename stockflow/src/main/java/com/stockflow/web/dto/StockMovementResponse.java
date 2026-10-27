package com.stockflow.web.dto;

import com.stockflow.domain.MovementType;
import com.stockflow.domain.StockMovement;

import java.time.Instant;

public record StockMovementResponse(
        Long id,
        Long productId,
        String productSku,
        MovementType type,
        int quantity,
        int balanceAfter,
        String reason,
        Long saleId,
        Instant createdAt,
        String createdBy) {

    public static StockMovementResponse from(StockMovement m) {
        return new StockMovementResponse(
                m.getId(),
                m.getProduct().getId(),
                m.getProduct().getSku(),
                m.getType(),
                m.getQuantity(),
                m.getBalanceAfter(),
                m.getReason(),
                m.getSale() == null ? null : m.getSale().getId(),
                m.getCreatedAt(),
                m.getCreatedBy());
    }
}
