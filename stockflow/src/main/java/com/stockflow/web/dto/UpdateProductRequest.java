package com.stockflow.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Atualização de cadastro. Repare que NÃO existe campo de estoque aqui:
 * a quantidade só muda por movimentações (entrada, saída, ajuste, venda).
 */
public record UpdateProductRequest(
        @NotBlank @Size(max = 150) String name,
        Long categoryId,
        Long supplierId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
        @NotNull @Min(0) @Max(1_000_000) Integer minStock,
        @NotNull Boolean active) {
}
