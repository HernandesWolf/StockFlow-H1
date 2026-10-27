package com.stockflow;

import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.IdempotencyKeyReuseException;
import com.stockflow.service.SaleResult;
import com.stockflow.web.dto.CreateSaleRequest;
import com.stockflow.web.dto.SaleItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A mesma requisição repetida (retry, duplo clique, timeout) nunca pode gerar duas vendas. */
class SaleIdempotencyIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Repetir a requisição com a mesma chave devolve a venda original e baixa o estoque uma vez")
    void repeatedRequestReturnsOriginalSale() {
        Long productId = newProduct(10);
        String key = newKey();

        SaleResult first = saleService.create(key, sale(productId, 3), "operador");
        SaleResult second = saleService.create(key, sale(productId, 3), "operador");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.sale().id()).isEqualTo(first.sale().id());
        assertThat(stockOf(productId)).isEqualTo(7);
        assertThat(countSaleItems(productId)).isEqualTo(1);
        assertLedgerMatchesStock(productId);
    }

    @Test
    @DisplayName("10 requisições simultâneas com a MESMA chave geram uma única venda")
    void simultaneousRequestsWithSameKeyCreateOneSale() throws Exception {
        Long productId = newProduct(10);
        String key = newKey();

        List<Outcome<SaleResult>> outcomes = runConcurrently(10,
                i -> saleService.create(key, sale(productId, 2), "operador"));

        outcomes.forEach(outcome -> assertThat(outcome.error()).isNull());
        assertThat(outcomes.stream().map(outcome -> outcome.value().sale().id()).distinct().count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.value().replayed()).count()).isEqualTo(1);
        assertThat(stockOf(productId)).isEqualTo(8);
        assertLedgerMatchesStock(productId);
    }

    @Test
    @DisplayName("Reutilizar a chave com conteúdo diferente é rejeitado")
    void reusingKeyWithDifferentContentIsRejected() {
        Long productId = newProduct(10);
        String key = newKey();
        saleService.create(key, sale(productId, 1), "operador");

        assertThatThrownBy(() -> saleService.create(key, sale(productId, 5), "operador"))
                .isInstanceOf(IdempotencyKeyReuseException.class);

        assertThat(stockOf(productId)).isEqualTo(9);
    }

    @Test
    @DisplayName("Outro usuário não consegue reaproveitar a chave de uma venda alheia")
    void anotherUserCannotReuseTheKey() {
        Long productId = newProduct(10);
        String key = newKey();
        saleService.create(key, sale(productId, 1), "maria");

        assertThatThrownBy(() -> saleService.create(key, sale(productId, 1), "joao"))
                .isInstanceOf(IdempotencyKeyReuseException.class);
    }

    @Test
    @DisplayName("A ordem dos itens não importa: o mesmo pedido em outra ordem é uma repetição")
    void itemOrderDoesNotMatter() {
        Long productA = newProduct(10);
        Long productB = newProduct(10);
        String key = newKey();
        CreateSaleRequest aThenB = new CreateSaleRequest(
                List.of(new SaleItemRequest(productA, 1), new SaleItemRequest(productB, 2)));
        CreateSaleRequest bThenA = new CreateSaleRequest(
                List.of(new SaleItemRequest(productB, 2), new SaleItemRequest(productA, 1)));

        SaleResult first = saleService.create(key, aThenB, "operador");
        SaleResult second = saleService.create(key, bThenA, "operador");

        assertThat(second.replayed()).isTrue();
        assertThat(second.sale().id()).isEqualTo(first.sale().id());
        assertThat(stockOf(productA)).isEqualTo(9);
        assertThat(stockOf(productB)).isEqualTo(8);
    }

    @Test
    @DisplayName("A Idempotency-Key é obrigatória")
    void keyIsRequired() {
        Long productId = newProduct(10);

        assertThatThrownBy(() -> saleService.create(null, sale(productId, 1), "operador"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> saleService.create("   ", sale(productId, 1), "operador"))
                .isInstanceOf(BadRequestException.class);
        assertThat(stockOf(productId)).isEqualTo(10);
    }
}
