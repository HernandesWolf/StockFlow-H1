package com.stockflow.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SaleItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(100_000) Integer quantity) {
}
