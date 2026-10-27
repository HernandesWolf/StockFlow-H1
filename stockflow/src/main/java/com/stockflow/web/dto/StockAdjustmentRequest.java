package com.stockflow.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ajuste de inventário: informa a quantidade CONTADA e a justificativa (obrigatória). */
public record StockAdjustmentRequest(
        @NotNull Long productId,
        @NotNull @Min(0) @Max(1_000_000) Integer countedQuantity,
        @NotBlank @Size(max = 255) String reason) {
}
