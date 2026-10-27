package com.stockflow.web.dto;

import com.stockflow.domain.Supplier;

public record SupplierResponse(Long id, String name, String email, String phone) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getName(), supplier.getEmail(), supplier.getPhone());
    }
}
