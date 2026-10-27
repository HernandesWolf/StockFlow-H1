package com.stockflow.service;

import com.stockflow.domain.Category;
import com.stockflow.domain.Money;
import com.stockflow.domain.MovementType;
import com.stockflow.domain.PriceHistory;
import com.stockflow.domain.Product;
import com.stockflow.domain.StockMovement;
import com.stockflow.domain.Supplier;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.NotFoundException;
import com.stockflow.repository.CategoryRepository;
import com.stockflow.repository.PriceHistoryRepository;
import com.stockflow.repository.ProductRepository;
import com.stockflow.repository.StockMovementRepository;
import com.stockflow.repository.SupplierRepository;
import com.stockflow.web.dto.CreateProductRequest;
import com.stockflow.web.dto.PageResponse;
import com.stockflow.web.dto.PriceHistoryResponse;
import com.stockflow.web.dto.ProductResponse;
import com.stockflow.web.dto.UpdateProductRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;
    private final PriceHistoryRepository priceHistory;
    private final StockMovementRepository movements;

    public ProductService(ProductRepository products,
                          CategoryRepository categories,
                          SupplierRepository suppliers,
                          PriceHistoryRepository priceHistory,
                          StockMovementRepository movements) {
        this.products = products;
        this.categories = categories;
        this.suppliers = suppliers;
        this.priceHistory = priceHistory;
        this.movements = movements;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request, String actor) {
        String sku = request.sku().trim().toUpperCase();
        if (products.existsBySku(sku)) {
            throw new ConflictException("Já existe um produto com o SKU " + sku + ".");
        }
        Product product = new Product(
                sku,
                request.name().trim(),
                findCategory(request.categoryId()),
                findSupplier(request.supplierId()),
                request.costPrice(),
                request.salePrice(),
                request.minStock() == null ? 0 : request.minStock());
        products.save(product);
        priceHistory.save(PriceHistory.initial(product, actor));

        int initialStock = request.initialStock() == null ? 0 : request.initialStock();
        if (initialStock > 0) {
            product.increaseStock(initialStock);
            movements.save(StockMovement.of(product, MovementType.IN, initialStock, "Estoque inicial", null, actor));
        }
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request, String actor) {
        Product product = find(id);

        BigDecimal newCost = Money.of(request.costPrice());
        BigDecimal newSale = Money.of(request.salePrice());
        boolean pricesChanged = product.getCostPrice().compareTo(newCost) != 0
                || product.getSalePrice().compareTo(newSale) != 0;
        if (pricesChanged) {
            // Registra o histórico ANTES de aplicar, guardando os valores antigos.
            priceHistory.save(PriceHistory.change(
                    product, product.getCostPrice(), newCost, product.getSalePrice(), newSale, actor));
            product.changePrices(newCost, newSale);
        }

        product.updateDetails(
                request.name().trim(),
                findCategory(request.categoryId()),
                findSupplier(request.supplierId()),
                request.minStock(),
                request.active());
        return ProductResponse.from(product);
    }

    /** Remoção lógica: o produto some das vendas, mas o histórico dele continua íntegro. */
    @Transactional
    public void deactivate(Long id) {
        find(id).deactivate();
    }

    public ProductResponse get(Long id) {
        return ProductResponse.from(find(id));
    }

    public PageResponse<ProductResponse> search(String search, Long categoryId, Long supplierId,
                                                Boolean active, Pageable pageable) {
        return PageResponse.of(
                products.findAll(ProductSpecifications.filter(search, categoryId, supplierId, active), pageable)
                        .map(ProductResponse::from));
    }

    public List<PriceHistoryResponse> priceHistory(Long productId) {
        find(productId); // garante o 404 se o produto não existir
        return priceHistory.findByProductIdOrderByChangedAtDescIdDesc(productId).stream()
                .map(PriceHistoryResponse::from)
                .toList();
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Produto", id));
    }

    private Category findCategory(Long id) {
        if (id == null) {
            return null;
        }
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Categoria", id));
    }

    private Supplier findSupplier(Long id) {
        if (id == null) {
            return null;
        }
        return suppliers.findById(id).orElseThrow(() -> new NotFoundException("Fornecedor", id));
    }
}
