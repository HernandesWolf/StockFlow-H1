package com.stockflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Lançamento no livro-razão do estoque. Nunca é alterado nem apagado
 * (o banco garante isso com um trigger). O estoque atual de um produto
 * é sempre igual à soma das variações (quantity) dos seus lançamentos.
 */
@Entity
@Table(name = "stock_movement")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    /** Variação: positiva = entrou, negativa = saiu. */
    @Column(nullable = false)
    private int quantity;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    protected StockMovement() {
        // exigido pelo JPA
    }

    private StockMovement(Product product, MovementType type, int quantity, String reason, Sale sale, String actor) {
        this.product = product;
        this.type = type;
        this.quantity = quantity;
        this.balanceAfter = product.getStockQuantity();
        this.reason = reason;
        this.sale = sale;
        this.createdBy = actor;
        this.createdAt = Instant.now();
    }

    /**
     * Cria o lançamento. IMPORTANTE: chame DEPOIS de alterar o estoque do produto,
     * porque o saldo após a operação é lido do próprio produto.
     */
    public static StockMovement of(Product product, MovementType type, int delta, String reason, Sale sale, String actor) {
        return new StockMovement(product, type, delta, reason, sale, actor);
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public MovementType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public Sale getSale() {
        return sale;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
