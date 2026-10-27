package com.stockflow.web.dto;

import com.stockflow.domain.Product;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        Long categoryId,
        String categoryName,
        Long supplierId,
        String supplierName,
        BigDecimal costPrice,
        BigDecimal salePrice,
        BigDecimal grossMarginPercent,
        int stockQuantity,
        int minStock,
        boolean lowStock,
        boolean active) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getSku(),
                p.getName(),
                p.getCategory() == null ? null : p.getCategory().getId(),
                p.getCategory() == null ? null : p.getCategory().getName(),
                p.getSupplier() == null ? null : p.getSupplier().getId(),
                p.getSupplier() == null ? null : p.getSupplier().getName(),
                p.getCostPrice(),
                p.getSalePrice(),
                p.grossMarginPercent(),
                p.getStockQuantity(),
                p.getMinStock(),
                p.isLowStock(),
                p.isActive());
    }
}
