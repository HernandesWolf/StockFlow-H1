package com.stockflow.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Saída que não é venda (perda, quebra...). O motivo é obrigatório. */
public record StockExitRequest(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(1_000_000) Integer quantity,
        @NotBlank @Size(max = 255) String reason) {
}
