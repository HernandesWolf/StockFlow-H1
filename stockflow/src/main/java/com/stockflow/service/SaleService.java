package com.stockflow.service;

import com.stockflow.domain.MovementType;
import com.stockflow.domain.Product;
import com.stockflow.domain.Sale;
import com.stockflow.domain.SaleItem;
import com.stockflow.domain.SaleStatus;
import com.stockflow.domain.StockMovement;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.BusinessRuleException;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.IdempotencyKeyReuseException;
import com.stockflow.exception.NotFoundException;
import com.stockflow.repository.ProductRepository;
import com.stockflow.repository.SaleRepository;
import com.stockflow.repository.StockMovementRepository;
import com.stockflow.web.dto.CreateSaleRequest;
import com.stockflow.web.dto.PageResponse;
import com.stockflow.web.dto.SaleResponse;
import com.stockflow.web.dto.SaleSummaryResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vendas. É aqui que moram as duas garantias mais importantes do sistema:
 *
 * 1) CONCORRÊNCIA - duas pessoas vendendo a última unidade ao mesmo tempo.
 *    Dentro de UMA transação, travamos as linhas dos produtos (SELECT ... FOR UPDATE, sempre em
 *    ordem de id) antes de conferir e baixar o estoque. A segunda venda espera a primeira terminar
 *    e, ao conferir o estoque, já enxerga o valor atualizado: recebe "estoque insuficiente".
 *    Se mesmo assim um bug deixasse passar, o CHECK (stock_quantity >= 0) do banco recusaria.
 *
 * 2) IDEMPOTÊNCIA - a mesma requisição repetida (timeout, duplo clique, retry de rede).
 *    O cliente envia o header Idempotency-Key. A chave tem UNIQUE no banco e é gravada como
 *    PRIMEIRA operação da transação. Repetir a requisição devolve a venda original em vez de
 *    criar outra. Duas requisições simultâneas com a mesma chave: a segunda espera a primeira
 *    confirmar, esbarra no UNIQUE, e então devolve a venda que a primeira criou.
 */
@Service
public class SaleService {

    private static final int MAX_KEY_LENGTH = 100;

    private final SaleRepository sales;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;

    public SaleService(SaleRepository sales,
                       ProductRepository products,
                       StockMovementRepository movements,
                       PlatformTransactionManager transactionManager) {
        this.sales = sales;
        this.products = products;
        this.movements = movements;
        this.writeTx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    /**
     * Registra uma venda. Não é @Transactional de propósito: precisamos controlar as transações
     * manualmente para conseguir "tentar de novo como replay" depois que uma transação falha
     * por chave duplicada (uma transação que falhou não pode ser reaproveitada).
     */
    public SaleResult create(String idempotencyKey, CreateSaleRequest request, String actor) {
        validateKey(idempotencyKey);
        List<SaleRequestHasher.Item> items = SaleRequestHasher.normalize(request.items());
        String hash = SaleRequestHasher.hash(actor, items);

        // Caminho comum de uma repetição: a venda já existe.
        Optional<SaleResponse> existing = findReplay(idempotencyKey, hash);
        if (existing.isPresent()) {
            return new SaleResult(existing.get(), true);
        }

        try {
            SaleResponse created = writeTx.execute(status -> doCreate(idempotencyKey, hash, items, actor));
            return new SaleResult(created, false);
        } catch (DataIntegrityViolationException e) {
            // Outra requisição com a MESMA chave confirmou primeiro (UNIQUE violado).
            // Se for isso, devolvemos a venda dela; se for outra violação, propagamos o erro.
            Optional<SaleResponse> winner = findReplay(idempotencyKey, hash);
            if (winner.isPresent()) {
                return new SaleResult(winner.get(), true);
            }
            throw e;
        }
    }

    private SaleResponse doCreate(String idempotencyKey, String hash, List<SaleRequestHasher.Item> items, String actor) {
        // 1) "Reserva" a chave de idempotência gravando a venda primeiro.
        Sale sale = sales.saveAndFlush(new Sale(idempotencyKey, hash, actor));

        // 2) Trava os produtos (em ordem de id, para evitar deadlock).
        List<Long> ids = items.stream().map(SaleRequestHasher.Item::productId).toList();
        Map<Long, Product> lockedProducts = products.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 3) Com as linhas travadas, confere e baixa o estoque, item a item.
        BigDecimal total = BigDecimal.ZERO;
        for (SaleRequestHasher.Item item : items) {
            Product product = lockedProducts.get(item.productId());
            if (product == null) {
                throw new NotFoundException("Produto", item.productId());
            }
            if (!product.isActive()) {
                throw new BusinessRuleException("O produto " + product.getSku() + " está inativo e não pode ser vendido.");
            }
            product.decreaseStock(item.quantity()); // lança InsufficientStockException -> rollback de TUDO

            SaleItem saleItem = new SaleItem(product, item.quantity(), product.getSalePrice(), product.getCostPrice());
            sale.addItem(saleItem);
            movements.save(StockMovement.of(product, MovementType.SALE, -item.quantity(),
                    "Venda #" + sale.getId(), sale, actor));
            total = total.add(saleItem.getLineTotal());
        }
        sale.setTotal(total);
        return SaleResponse.from(sale);
    }

    /**
     * Cancela a venda e devolve as unidades ao estoque, tudo na mesma transação.
     * A venda é travada (FOR UPDATE): dois cancelamentos simultâneos não revertem o estoque duas vezes.
     */
    @Transactional
    public SaleResponse cancel(Long saleId, String reason, String actor) {
        Sale sale = sales.findByIdForUpdate(saleId)
                .orElseThrow(() -> new NotFoundException("Venda", saleId));
        // Com a venda travada, esta checagem é confiável: o segundo cancelamento simultâneo cai aqui.
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new ConflictException("A venda " + saleId + " já está cancelada.");
        }

        List<Long> ids = sale.getItems().stream().map(item -> item.getProduct().getId()).sorted().toList();
        Map<Long, Product> lockedProducts = products.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (SaleItem item : sale.getItems()) {
            Product product = lockedProducts.get(item.getProduct().getId());
            product.increaseStock(item.getQuantity());
            movements.save(StockMovement.of(product, MovementType.SALE_CANCEL, item.getQuantity(),
                    "Cancelamento da venda #" + sale.getId() + ": " + reason.trim(), sale, actor));
        }
        sale.cancel(reason.trim(), actor);
        return SaleResponse.from(sale);
    }

    @Transactional(readOnly = true)
    public SaleResponse get(Long id) {
        return sales.findById(id).map(SaleResponse::from)
                .orElseThrow(() -> new NotFoundException("Venda", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleSummaryResponse> list(SaleStatus status, Pageable pageable) {
        Page<Sale> page = status == null ? sales.findAll(pageable) : sales.findByStatus(status, pageable);
        return PageResponse.of(page.map(SaleSummaryResponse::from));
    }

    /** Procura uma venda anterior com a mesma chave. Se o conteúdo for diferente, é erro do cliente. */
    private Optional<SaleResponse> findReplay(String idempotencyKey, String hash) {
        return readTx.execute(status -> sales.findByIdempotencyKey(idempotencyKey).map(sale -> {
            if (!sale.getRequestHash().equals(hash)) {
                throw new IdempotencyKeyReuseException(idempotencyKey);
            }
            return SaleResponse.from(sale);
        }));
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("O header Idempotency-Key é obrigatório.");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("A Idempotency-Key deve ter no máximo " + MAX_KEY_LENGTH + " caracteres.");
        }
    }
}
