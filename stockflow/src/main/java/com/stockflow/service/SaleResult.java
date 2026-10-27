package com.stockflow.service;

import com.stockflow.web.dto.SaleResponse;

/**
 * Resultado do registro de venda.
 *
 * @param sale     a venda (nova ou a original, em caso de repetição)
 * @param replayed true quando a requisição era uma repetição de uma Idempotency-Key já processada
 */
public record SaleResult(SaleResponse sale, boolean replayed) {
}
