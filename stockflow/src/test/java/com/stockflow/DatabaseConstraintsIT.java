package com.stockflow;

import com.stockflow.service.SaleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o banco "por fora" da aplicação: mesmo que um bug ou alguém com acesso direto ao banco
 * tente violar as regras, o PostgreSQL recusa. É a última linha de defesa.
 */
class DatabaseConstraintsIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("O banco recusa estoque negativo")
    void databaseRejectsNegativeStock() {
        Long productId = newProduct(3);

        assertThatThrownBy(() -> jdbc.update("update product set stock_quantity = -1 where id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("O banco recusa preço negativo")
    void databaseRejectsNegativePrice() {
        Long productId = newProduct(3);

        assertThatThrownBy(() -> jdbc.update("update product set sale_price = -0.01 where id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Lançamentos de estoque são append-only: UPDATE e DELETE são proibidos")
    void stockMovementsAreAppendOnly() {
        Long productId = newProduct(3);

        assertThatThrownBy(() -> jdbc.update("update stock_movement set reason = 'adulterado' where product_id = ?", productId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from stock_movement where product_id = ?", productId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("Histórico de preços é append-only")
    void priceHistoryIsAppendOnly() {
        Long productId = newProduct(3);

        assertThatThrownBy(() -> jdbc.update("delete from price_history where product_id = ?", productId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("Vendas não podem ser apagadas, apenas canceladas")
    void salesCannotBeDeleted() {
        Long productId = newProduct(3);
        SaleResult sold = saleService.create(newKey(), sale(productId, 1), "operador");

        assertThatThrownBy(() -> jdbc.update("delete from sale where id = ?", sold.sale().id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("cancele a venda");
    }

    @Test
    @DisplayName("O banco impede duas vendas com a mesma Idempotency-Key")
    void databaseRejectsDuplicateIdempotencyKey() {
        String key = newKey();
        String insert = "insert into sale (status, total, idempotency_key, request_hash, created_by) "
                + "values ('COMPLETED', 0, ?, 'hash', 'teste')";
        jdbc.update(insert, key);

        assertThatThrownBy(() -> jdbc.update(insert, key)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("Uma venda cancelada precisa ter data e motivo do cancelamento")
    void cancelledSaleRequiresReason() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into sale (status, total, idempotency_key, request_hash, created_by) "
                        + "values ('CANCELLED', 0, ?, 'hash', 'teste')", newKey()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
