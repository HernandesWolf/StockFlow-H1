package com.stockflow.domain;

import com.stockflow.exception.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sale")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status = SaleStatus.COMPLETED;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = Money.zero();

    /** Chave enviada pelo cliente no header Idempotency-Key. Única no banco. */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /** Resumo (SHA-256) do conteúdo da requisição, para detectar reuso indevido da chave. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 60)
    private String createdBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 60)
    private String cancelledBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<SaleItem> items = new ArrayList<>();

    protected Sale() {
        // exigido pelo JPA
    }

    public Sale(String idempotencyKey, String requestHash, String createdBy) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void addItem(SaleItem item) {
        items.add(item);
        item.setSale(this);
    }

    public void setTotal(BigDecimal total) {
        this.total = Money.of(total);
    }

    public void cancel(String reason, String actor) {
        if (status == SaleStatus.CANCELLED) {
            throw new ConflictException("A venda " + id + " já está cancelada.");
        }
        this.status = SaleStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledBy = actor;
        this.cancelledAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public List<SaleItem> getItems() {
        return items;
    }
}
