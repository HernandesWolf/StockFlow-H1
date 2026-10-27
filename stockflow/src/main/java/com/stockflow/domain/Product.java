package com.stockflow.domain;

import com.stockflow.exception.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Produto. As regras do estoque vivem AQUI (e não espalhadas em serviços):
 * a única forma de mexer na quantidade é por increaseStock / decreaseStock / adjustTo,
 * e nenhuma delas deixa o estoque ficar negativo.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "min_stock", nullable = false)
    private int minStock;

    @Column(nullable = false)
    private boolean active = true;

    /** Controle de concorrência otimista para edições de cadastro (nome, preço...). */
    @Version
    @Column(nullable = false)
    private long version;

    protected Product() {
        // exigido pelo JPA
    }

    public Product(String sku, String name, Category category, Supplier supplier,
                   BigDecimal costPrice, BigDecimal salePrice, int minStock) {
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.supplier = supplier;
        this.costPrice = Money.of(costPrice);
        this.salePrice = Money.of(salePrice);
        this.minStock = minStock;
    }

    // ------------------------------------------------------------------
    // Regras de estoque
    // ------------------------------------------------------------------

    /** Retira unidades do estoque. Lança InsufficientStockException se não houver o suficiente. */
    public void decreaseStock(int quantity) {
        requirePositive(quantity);
        if (quantity > stockQuantity) {
            throw new InsufficientStockException(sku, stockQuantity, quantity);
        }
        stockQuantity -= quantity;
    }

    public void increaseStock(int quantity) {
        requirePositive(quantity);
        stockQuantity = Math.addExact(stockQuantity, quantity);
    }

    /** Acerta o estoque para a contagem física do inventário. */
    public void adjustTo(int countedQuantity) {
        if (countedQuantity < 0) {
            throw new IllegalArgumentException("A contagem do inventário não pode ser negativa.");
        }
        this.stockQuantity = countedQuantity;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        }
    }

    public boolean isLowStock() {
        return stockQuantity <= minStock;
    }

    // ------------------------------------------------------------------
    // Preço e margem
    // ------------------------------------------------------------------

    public void changePrices(BigDecimal newCostPrice, BigDecimal newSalePrice) {
        this.costPrice = Money.of(newCostPrice);
        this.salePrice = Money.of(newSalePrice);
    }

    /** Margem bruta unitária estimada: preço de venda - custo. */
    public BigDecimal unitGrossMargin() {
        return salePrice.subtract(costPrice);
    }

    /** Margem bruta em % sobre o preço de venda: (venda - custo) / venda * 100. */
    public BigDecimal grossMarginPercent() {
        if (salePrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return unitGrossMargin()
                .multiply(BigDecimal.valueOf(100))
                .divide(salePrice, 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Cadastro
    // ------------------------------------------------------------------

    public void updateDetails(String name, Category category, Supplier supplier, int minStock, boolean active) {
        this.name = name;
        this.category = category;
        this.supplier = supplier;
        this.minStock = minStock;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public int getMinStock() {
        return minStock;
    }

    public boolean isActive() {
        return active;
    }
}
