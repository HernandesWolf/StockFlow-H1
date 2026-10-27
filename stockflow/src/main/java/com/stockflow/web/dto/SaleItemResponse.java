package com.stockflow.web.dto;

import com.stockflow.domain.SaleItem;

import java.math.BigDecimal;

public record SaleItemResponse(
        Long productId,
        String productSku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {

    public static SaleItemResponse from(SaleItem item) {
        return new SaleItemResponse(
                item.getProduct().getId(),
                item.getProduct().getSku(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }
}
