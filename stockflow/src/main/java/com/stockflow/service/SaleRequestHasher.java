package com.stockflow.service;

import com.stockflow.web.dto.SaleItemRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Normaliza o pedido de venda e calcula um "resumo" (hash) dele.
 *
 * Normalizar = juntar itens repetidos do mesmo produto e ordenar por id. Assim, dois pedidos
 * equivalentes ("A:1, B:2" e "B:2, A:1") geram exatamente o mesmo hash e travam as linhas
 * do banco sempre na mesma ordem (o que evita deadlock).
 */
public final class SaleRequestHasher {

    /** Item já normalizado. */
    public record Item(Long productId, int quantity) {
    }

    private SaleRequestHasher() {
    }

    public static List<Item> normalize(List<SaleItemRequest> items) {
        Map<Long, Integer> merged = new TreeMap<>();
        for (SaleItemRequest item : items) {
            merged.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return merged.entrySet().stream()
                .map(entry -> new Item(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** O hash inclui quem fez a venda: outro usuário reutilizando a chave recebe erro, não a venda alheia. */
    public static String hash(String actor, List<Item> normalizedItems) {
        StringBuilder canonical = new StringBuilder(actor).append('|');
        for (Item item : normalizedItems) {
            canonical.append(item.productId()).append(':').append(item.quantity()).append(';');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
