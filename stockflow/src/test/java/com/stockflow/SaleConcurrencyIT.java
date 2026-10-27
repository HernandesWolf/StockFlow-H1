package com.stockflow;

import com.stockflow.exception.InsufficientStockException;
import com.stockflow.service.SaleResult;
import com.stockflow.web.dto.CreateSaleRequest;
import com.stockflow.web.dto.SaleItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Provas de que o sistema não vende o que não tem, mesmo com muitas requisições simultâneas.
 * Estes testes só fazem sentido contra um banco real (por isso o Testcontainers).
 */
class SaleConcurrencyIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("12 pessoas tentam vender a ÚLTIMA unidade ao mesmo tempo: só uma venda conclui")
    void onlyOneSaleWinsTheLastUnit() throws Exception {
        Long productId = newProduct(1);

        List<Outcome<SaleResult>> outcomes = runConcurrently(12,
                i -> saleService.create(newKey(), sale(productId, 1), "operador-" + i));

        assertThat(outcomes.stream().filter(Outcome::ok).count()).isEqualTo(1);
        outcomes.stream().filter(outcome -> !outcome.ok()).forEach(outcome ->
                assertThat(outcome.error()).isInstanceOf(InsufficientStockException.class));
        assertThat(stockOf(productId)).isZero();
        assertThat(countSaleItems(productId)).isEqualTo(1);
        assertLedgerMatchesStock(productId);
    }

    @Test
    @DisplayName("Com 10 unidades e 25 compradores simultâneos, exatamente 10 vendas concluem")
    void exactlyAsManySalesAsUnitsInStock() throws Exception {
        Long productId = newProduct(10);

        List<Outcome<SaleResult>> outcomes = runConcurrently(25,
                i -> saleService.create(newKey(), sale(productId, 1), "operador-" + i));

        assertThat(outcomes.stream().filter(Outcome::ok).count()).isEqualTo(10);
        assertThat(outcomes.stream().filter(outcome -> !outcome.ok()).count()).isEqualTo(15);
        outcomes.stream().filter(outcome -> !outcome.ok()).forEach(outcome ->
                assertThat(outcome.error()).isInstanceOf(InsufficientStockException.class));
        assertThat(stockOf(productId)).isZero();
        assertThat(countSaleItems(productId)).isEqualTo(10);
        assertLedgerMatchesStock(productId);
    }

    @Test
    @DisplayName("Vendas com os mesmos produtos em ordens opostas não causam deadlock")
    void salesWithOppositeItemOrderDoNotDeadlock() throws Exception {
        Long productA = newProduct(20);
        Long productB = newProduct(20);
        CreateSaleRequest aThenB = new CreateSaleRequest(
                List.of(new SaleItemRequest(productA, 1), new SaleItemRequest(productB, 1)));
        CreateSaleRequest bThenA = new CreateSaleRequest(
                List.of(new SaleItemRequest(productB, 1), new SaleItemRequest(productA, 1)));

        List<Outcome<SaleResult>> outcomes = runConcurrently(20,
                i -> saleService.create(newKey(), i % 2 == 0 ? aThenB : bThenA, "operador-" + i));

        outcomes.forEach(outcome -> assertThat(outcome.error()).isNull());
        assertThat(stockOf(productA)).isZero();
        assertThat(stockOf(productB)).isZero();
        assertLedgerMatchesStock(productA);
        assertLedgerMatchesStock(productB);
    }

    @Test
    @DisplayName("Venda com vários itens é atômica: se um item falha, nada é gravado")
    void failedMultiItemSaleLeavesNoTrace() {
        Long available = newProduct(5);
        Long outOfStock = newProduct(0);
        String key = newKey();
        CreateSaleRequest request = new CreateSaleRequest(
                List.of(new SaleItemRequest(available, 1), new SaleItemRequest(outOfStock, 1)));

        assertThatThrownBy(() -> saleService.create(key, request, "operador"))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(stockOf(available)).isEqualTo(5);
        assertThat(stockOf(outOfStock)).isZero();
        Integer salesWithKey = jdbc.queryForObject(
                "select count(*) from sale where idempotency_key = ?", Integer.class, key);
        assertThat(salesWithKey).isZero();
        assertLedgerMatchesStock(available);
    }
}
