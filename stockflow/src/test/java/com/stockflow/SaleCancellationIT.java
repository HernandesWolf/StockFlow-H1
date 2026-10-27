package com.stockflow;

import com.stockflow.domain.SaleStatus;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.NotFoundException;
import com.stockflow.service.SaleResult;
import com.stockflow.web.dto.SaleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaleCancellationIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Cancelar uma venda devolve o estoque e registra os lançamentos de reversão")
    void cancellationRestoresStockAndRecordsMovements() {
        Long productId = newProduct(5);
        SaleResult sold = saleService.create(newKey(), sale(productId, 3), "operador");
        assertThat(stockOf(productId)).isEqualTo(2);

        SaleResponse cancelled = saleService.cancel(sold.sale().id(), "Cliente desistiu", "admin");

        assertThat(cancelled.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("Cliente desistiu");
        assertThat(cancelled.cancelledBy()).isEqualTo("admin");
        assertThat(stockOf(productId)).isEqualTo(5);

        List<String> types = jdbc.queryForList(
                "select type from stock_movement where product_id = ? order by id", String.class, productId);
        assertThat(types).containsExactly("IN", "SALE", "SALE_CANCEL");
        assertLedgerMatchesStock(productId);
    }

    @Test
    @DisplayName("Cancelar a mesma venda duas vezes falha e não devolve o estoque em dobro")
    void cancellingTwiceFails() {
        Long productId = newProduct(5);
        SaleResult sold = saleService.create(newKey(), sale(productId, 3), "operador");
        saleService.cancel(sold.sale().id(), "Primeiro cancelamento", "admin");

        assertThatThrownBy(() -> saleService.cancel(sold.sale().id(), "Segundo cancelamento", "admin"))
                .isInstanceOf(ConflictException.class);

        assertThat(stockOf(productId)).isEqualTo(5);
        assertLedgerMatchesStock(productId);
    }

    @Test
    @DisplayName("8 cancelamentos simultâneos da mesma venda: só um tem efeito")
    void simultaneousCancellationsRevertStockOnlyOnce() throws Exception {
        Long productId = newProduct(5);
        SaleResult sold = saleService.create(newKey(), sale(productId, 4), "operador");
        Long saleId = sold.sale().id();

        List<Outcome<SaleResponse>> outcomes = runConcurrently(8,
                i -> saleService.cancel(saleId, "Cancelamento " + i, "admin"));

        assertThat(outcomes.stream().filter(Outcome::ok).count()).isEqualTo(1);
        outcomes.stream().filter(outcome -> !outcome.ok()).forEach(outcome ->
                assertThat(outcome.error()).isInstanceOf(ConflictException.class));
        assertThat(stockOf(productId)).isEqualTo(5);
        assertLedgerMatchesStock(productId);
    }

    @Test
    @DisplayName("Cancelar uma venda inexistente gera NotFoundException")
    void cancellingUnknownSaleFails() {
        assertThatThrownBy(() -> saleService.cancel(999_999_999L, "Motivo", "admin"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Uma venda cancelada continua acessível, com o status CANCELLED")
    void cancelledSaleStaysVisible() {
        Long productId = newProduct(5);
        SaleResult sold = saleService.create(newKey(), sale(productId, 1), "operador");
        saleService.cancel(sold.sale().id(), "Erro de digitação", "admin");

        SaleResponse found = saleService.get(sold.sale().id());

        assertThat(found.status()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(found.items()).hasSize(1);
    }
}
