package com.stockflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Histórico (somente inserção) de alterações de custo e preço de venda. */
@Entity
@Table(name = "price_history")
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "old_cost_price", precision = 12, scale = 2)
    private BigDecimal oldCostPrice;

    @Column(name = "new_cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal newCostPrice;

    @Column(name = "old_sale_price", precision = 12, scale = 2)
    private BigDecimal oldSalePrice;

    @Column(name = "new_sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal newSalePrice;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "changed_by", nullable = false, length = 60)
    private String changedBy;

    protected PriceHistory() {
        // exigido pelo JPA
    }

    private PriceHistory(Product product, BigDecimal oldCostPrice, BigDecimal newCostPrice,
                         BigDecimal oldSalePrice, BigDecimal newSalePrice, String changedBy) {
        this.product = product;
        this.oldCostPrice = oldCostPrice;
        this.newCostPrice = newCostPrice;
        this.oldSalePrice = oldSalePrice;
        this.newSalePrice = newSalePrice;
        this.changedBy = changedBy;
        this.changedAt = Instant.now();
    }

    /** Primeiro registro de preço do produto (não há valores antigos). */
    public static PriceHistory initial(Product product, String actor) {
        return new PriceHistory(product, null, product.getCostPrice(), null, product.getSalePrice(), actor);
    }

    public static PriceHistory change(Product product, BigDecimal oldCost, BigDecimal newCost,
                                      BigDecimal oldSale, BigDecimal newSale, String actor) {
        return new PriceHistory(product, oldCost, newCost, oldSale, newSale, actor);
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getOldCostPrice() {
        return oldCostPrice;
    }

    public BigDecimal getNewCostPrice() {
        return newCostPrice;
    }

    public BigDecimal getOldSalePrice() {
        return oldSalePrice;
    }

    public BigDecimal getNewSalePrice() {
        return newSalePrice;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }
}
