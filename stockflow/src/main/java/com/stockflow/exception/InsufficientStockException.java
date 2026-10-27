package com.stockflow.exception;

/** Tentativa de retirar mais unidades do que existem em estoque (HTTP 409). */
public class InsufficientStockException extends ConflictException {

    private final String productSku;
    private final int available;
    private final int requested;

    public InsufficientStockException(String productSku, int available, int requested) {
        super("Estoque insuficiente para o produto " + productSku
                + ": disponível " + available + ", solicitado " + requested + ".");
        this.productSku = productSku;
        this.available = available;
        this.requested = requested;
    }

    public String getProductSku() {
        return productSku;
    }

    public int getAvailable() {
        return available;
    }

    public int getRequested() {
        return requested;
    }
}
