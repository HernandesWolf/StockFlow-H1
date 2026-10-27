package com.stockflow.service;

import com.stockflow.domain.MovementType;
import com.stockflow.domain.Product;
import com.stockflow.domain.StockMovement;
import com.stockflow.exception.BusinessRuleException;
import com.stockflow.exception.InsufficientStockException;
import com.stockflow.exception.NotFoundException;
import com.stockflow.repository.ProductRepository;
import com.stockflow.repository.StockMovementRepository;
import com.stockflow.web.dto.StockAdjustmentRequest;
import com.stockflow.web.dto.StockEntryRequest;
import com.stockflow.web.dto.StockExitRequest;
import com.stockflow.web.dto.StockMovementResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private ProductRepository products;

    @Mock
    private StockMovementRepository movements;

    @InjectMocks
    private StockService service;

    private Product productWithStock(int stock) {
        Product product = new Product("SKU-1", "Caneta", null, null,
                new BigDecimal("2.00"), new BigDecimal("5.00"), 2);
        if (stock > 0) {
            product.increaseStock(stock);
        }
        ReflectionTestUtils.setField(product, "id", 1L);
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        return product;
    }

    private void movementSavingEchoesArgument() {
        when(movements.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Entrada soma ao estoque e grava o lançamento com o saldo final")
    void entryIncreasesStockAndRecordsMovement() {
        Product product = productWithStock(5);
        movementSavingEchoesArgument();

        StockMovementResponse response = service.registerEntry(new StockEntryRequest(1L, 10, "Compra"), "admin");

        assertThat(product.getStockQuantity()).isEqualTo(15);
        assertThat(response.type()).isEqualTo(MovementType.IN);
        assertThat(response.quantity()).isEqualTo(10);
        assertThat(response.balanceAfter()).isEqualTo(15);
        assertThat(response.reason()).isEqualTo("Compra");
    }

    @Test
    @DisplayName("Saída subtrai do estoque e grava a variação negativa")
    void exitDecreasesStockAndRecordsNegativeDelta() {
        Product product = productWithStock(10);
        movementSavingEchoesArgument();

        StockMovementResponse response = service.registerExit(new StockExitRequest(1L, 4, "Quebra"), "admin");

        assertThat(product.getStockQuantity()).isEqualTo(6);
        assertThat(response.type()).isEqualTo(MovementType.OUT);
        assertThat(response.quantity()).isEqualTo(-4);
        assertThat(response.balanceAfter()).isEqualTo(6);
    }

    @Test
    @DisplayName("Saída maior que o estoque falha e não grava nenhum lançamento")
    void exitLargerThanStockFailsWithoutRecording() {
        Product product = productWithStock(2);

        assertThatThrownBy(() -> service.registerExit(new StockExitRequest(1L, 5, "Quebra"), "admin"))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(product.getStockQuantity()).isEqualTo(2);
        verify(movements, never()).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("Ajuste de inventário registra a diferença entre a contagem e o sistema")
    void adjustmentRecordsTheDifference() {
        Product product = productWithStock(8);
        movementSavingEchoesArgument();

        StockMovementResponse response = service.adjust(new StockAdjustmentRequest(1L, 5, "Contagem física"), "admin");

        assertThat(product.getStockQuantity()).isEqualTo(5);
        assertThat(response.type()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(response.quantity()).isEqualTo(-3);
        assertThat(response.balanceAfter()).isEqualTo(5);
    }

    @Test
    @DisplayName("Ajuste com contagem igual ao estoque é rejeitado")
    void adjustmentWithSameCountIsRejected() {
        productWithStock(8);

        assertThatThrownBy(() -> service.adjust(new StockAdjustmentRequest(1L, 8, "Contagem física"), "admin"))
                .isInstanceOf(BusinessRuleException.class);

        verify(movements, never()).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("Produto inexistente gera NotFoundException")
    void unknownProductIsNotFound() {
        when(products.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerEntry(new StockEntryRequest(99L, 1, null), "admin"))
                .isInstanceOf(NotFoundException.class);
    }
}
