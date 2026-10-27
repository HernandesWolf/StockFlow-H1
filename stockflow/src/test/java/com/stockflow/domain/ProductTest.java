package com.stockflow.domain;

import com.stockflow.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private static Product product(int initialStock) {
        Product product = new Product("SKU-1", "Caneta azul", null, null,
                new BigDecimal("2.00"), new BigDecimal("5.00"), 3);
        if (initialStock > 0) {
            product.increaseStock(initialStock);
        }
        return product;
    }

    @Test
    @DisplayName("A margem bruta percentual é calculada sobre o preço de venda")
    void grossMarginIsCalculatedOverSalePrice() {
        Product product = product(0);

        assertThat(product.unitGrossMargin()).isEqualByComparingTo("3.00");
        assertThat(product.grossMarginPercent()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("Produto com preço de venda zero tem margem zero (sem dividir por zero)")
    void marginIsZeroWhenSalePriceIsZero() {
        Product gift = new Product("SKU-2", "Brinde", null, null, new BigDecimal("1.00"), BigDecimal.ZERO, 0);

        assertThat(gift.grossMarginPercent()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Retirar mais do que existe lança exceção e não altera o estoque")
    void decreasingMoreThanAvailableFailsAndKeepsStock() {
        Product product = product(2);

        assertThatThrownBy(() -> product.decreaseStock(3))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("SKU-1");
        assertThat(product.getStockQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Retirar exatamente o que existe zera o estoque")
    void decreasingExactlyTheAvailableQuantityLeavesZero() {
        Product product = product(4);

        product.decreaseStock(4);

        assertThat(product.getStockQuantity()).isZero();
    }

    @Test
    @DisplayName("Quantidades zero ou negativas são rejeitadas")
    void nonPositiveQuantitiesAreRejected() {
        Product product = product(5);

        assertThatThrownBy(() -> product.decreaseStock(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.increaseStock(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(product.getStockQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("O alerta de estoque mínimo dispara quando o estoque é igual ou menor que o mínimo")
    void lowStockAlertTriggersAtOrBelowMinimum() {
        Product product = product(4); // mínimo = 3

        assertThat(product.isLowStock()).isFalse();

        product.decreaseStock(1); // estoque = 3
        assertThat(product.isLowStock()).isTrue();
    }

    @Test
    @DisplayName("Contagem de inventário negativa é rejeitada")
    void negativeInventoryCountIsRejected() {
        Product product = product(4);

        assertThatThrownBy(() -> product.adjustTo(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(product.getStockQuantity()).isEqualTo(4);
    }
}
