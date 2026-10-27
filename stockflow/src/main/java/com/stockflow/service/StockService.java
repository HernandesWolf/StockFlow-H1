package com.stockflow.service;

import com.stockflow.domain.MovementType;
import com.stockflow.domain.Product;
import com.stockflow.domain.StockMovement;
import com.stockflow.exception.BusinessRuleException;
import com.stockflow.exception.NotFoundException;
import com.stockflow.repository.ProductRepository;
import com.stockflow.repository.StockMovementRepository;
import com.stockflow.web.dto.PageResponse;
import com.stockflow.web.dto.StockAdjustmentRequest;
import com.stockflow.web.dto.StockEntryRequest;
import com.stockflow.web.dto.StockExitRequest;
import com.stockflow.web.dto.StockMovementResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Movimentações manuais de estoque. Toda operação:
 *  1) trava a linha do produto (SELECT ... FOR UPDATE), para que duas operações simultâneas
 *     sobre o mesmo produto sejam executadas uma de cada vez;
 *  2) altera o estoque;
 *  3) grava o lançamento no livro-razão (stock_movement) na MESMA transação.
 */
@Service
public class StockService {

    private final ProductRepository products;
    private final StockMovementRepository movements;

    public StockService(ProductRepository products, StockMovementRepository movements) {
        this.products = products;
        this.movements = movements;
    }

    @Transactional
    public StockMovementResponse registerEntry(StockEntryRequest request, String actor) {
        Product product = lock(request.productId());
        product.increaseStock(request.quantity());
        StockMovement movement = StockMovement.of(product, MovementType.IN, request.quantity(),
                textOrDefault(request.reason(), "Entrada de estoque"), null, actor);
        return StockMovementResponse.from(movements.save(movement));
    }

    @Transactional
    public StockMovementResponse registerExit(StockExitRequest request, String actor) {
        Product product = lock(request.productId());
        product.decreaseStock(request.quantity()); // lança InsufficientStockException se faltar
        StockMovement movement = StockMovement.of(product, MovementType.OUT, -request.quantity(),
                request.reason().trim(), null, actor);
        return StockMovementResponse.from(movements.save(movement));
    }

    /** Inventário: o operador informa a quantidade contada e o sistema registra a diferença. */
    @Transactional
    public StockMovementResponse adjust(StockAdjustmentRequest request, String actor) {
        Product product = lock(request.productId());
        int delta = request.countedQuantity() - product.getStockQuantity();
        if (delta == 0) {
            throw new BusinessRuleException("A contagem informada é igual ao estoque atual; não há o que ajustar.");
        }
        product.adjustTo(request.countedQuantity());
        StockMovement movement = StockMovement.of(product, MovementType.ADJUSTMENT, delta,
                request.reason().trim(), null, actor);
        return StockMovementResponse.from(movements.save(movement));
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> list(Long productId, MovementType type, Pageable pageable) {
        Specification<StockMovement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResponse.of(movements.findAll(spec, pageable).map(StockMovementResponse::from));
    }

    private Product lock(Long productId) {
        return products.findByIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("Produto", productId));
    }

    private static String textOrDefault(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.trim();
    }
}
