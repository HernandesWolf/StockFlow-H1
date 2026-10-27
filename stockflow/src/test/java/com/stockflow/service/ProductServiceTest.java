package com.stockflow.service;

import com.stockflow.domain.MovementType;
import com.stockflow.domain.PriceHistory;
import com.stockflow.domain.Product;
import com.stockflow.domain.StockMovement;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.NotFoundException;
import com.stockflow.repository.CategoryRepository;
import com.stockflow.repository.PriceHistoryRepository;
import com.stockflow.repository.ProductRepository;
import com.stockflow.repository.StockMovementRepository;
import com.stockflow.repository.SupplierRepository;
import com.stockflow.web.dto.CreateProductRequest;
import com.stockflow.web.dto.ProductResponse;
import com.stockflow.web.dto.UpdateProductRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository products;

    @Mock
    private CategoryRepository categories;

    @Mock
    private SupplierRepository suppliers;

    @Mock
    private PriceHistoryRepository priceHistory;

    @Mock
    private StockMovementRepository movements;

    @InjectMocks
    private ProductService service;

    private static Product existingProduct() {
        return new Product("CAN-001", "Caneta", null, null,
                new BigDecimal("2.00"), new BigDecimal("5.00"), 2);
    }

    @Test
    @DisplayName("SKU repetido (ignorando espaços e maiúsculas) é recusado")
    void duplicateSkuIsRejected() {
        when(products.existsBySku("CAN-001")).thenReturn(true);
        CreateProductRequest request = new CreateProductRequest(" can-001 ", "Caneta", null, null,
                new BigDecimal("2.00"), new BigDecimal("5.00"), 2, 0);

        assertThatThrownBy(() -> service.create(request, "admin")).isInstanceOf(ConflictException.class);

        verify(products, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Criar com estoque inicial grava o histórico de preço e um lançamento de entrada")
    void createWithInitialStockRecordsPriceHistoryAndMovement() {
        CreateProductRequest request = new CreateProductRequest("can-001", "Caneta", null, null,
                new BigDecimal("2.00"), new BigDecimal("5.00"), 2, 5);

        ProductResponse response = service.create(request, "admin");

        assertThat(response.sku()).isEqualTo("CAN-001");
        assertThat(response.stockQuantity()).isEqualTo(5);

        verify(priceHistory).save(any(PriceHistory.class));
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(MovementType.IN);
        assertThat(captor.getValue().getQuantity()).isEqualTo(5);
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(5);
    }

    @Test
    @DisplayName("Criar sem estoque inicial não gera lançamento de estoque")
    void createWithoutInitialStockRecordsNoMovement() {
        CreateProductRequest request = new CreateProductRequest("CAN-002", "Lápis", null, null,
                new BigDecimal("1.00"), new BigDecimal("2.00"), null, null);

        ProductResponse response = service.create(request, "admin");

        assertThat(response.stockQuantity()).isZero();
        verify(movements, never()).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("Alterar o preço grava o histórico com os valores antigos e novos")
    void changingPriceRecordsHistory() {
        Product product = existingProduct();
        when(products.findById(1L)).thenReturn(Optional.of(product));
        UpdateProductRequest request = new UpdateProductRequest("Caneta", null, null,
                new BigDecimal("2.00"), new BigDecimal("6.50"), 2, true);

        ProductResponse response = service.update(1L, request, "admin");

        ArgumentCaptor<PriceHistory> captor = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistory).save(captor.capture());
        PriceHistory history = captor.getValue();
        assertThat(history.getOldSalePrice()).isEqualByComparingTo("5.00");
        assertThat(history.getNewSalePrice()).isEqualByComparingTo("6.50");
        assertThat(history.getChangedBy()).isEqualTo("admin");
        assertThat(response.salePrice()).isEqualByComparingTo("6.50");
    }

    @Test
    @DisplayName("Atualizar sem mudar preços não grava histórico")
    void updatingWithoutPriceChangeRecordsNoHistory() {
        Product product = existingProduct();
        when(products.findById(1L)).thenReturn(Optional.of(product));
        UpdateProductRequest request = new UpdateProductRequest("Caneta esferográfica", null, null,
                new BigDecimal("2.00"), new BigDecimal("5.00"), 4, true);

        ProductResponse response = service.update(1L, request, "admin");

        verify(priceHistory, never()).save(any(PriceHistory.class));
        assertThat(response.name()).isEqualTo("Caneta esferográfica");
        assertThat(response.minStock()).isEqualTo(4);
    }

    @Test
    @DisplayName("Atualizar produto inexistente gera NotFoundException")
    void updatingUnknownProductIsNotFound() {
        when(products.findById(99L)).thenReturn(Optional.empty());
        UpdateProductRequest request = new UpdateProductRequest("X", null, null,
                new BigDecimal("1.00"), new BigDecimal("2.00"), 0, true);

        assertThatThrownBy(() -> service.update(99L, request, "admin")).isInstanceOf(NotFoundException.class);
    }
}
