package com.stockflow.service;

import com.stockflow.service.SaleRequestHasher.Item;
import com.stockflow.web.dto.SaleItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SaleRequestHasherTest {

    @Test
    @DisplayName("Normalizar junta itens repetidos e ordena por produto")
    void normalizeMergesDuplicatesAndSortsByProduct() {
        List<Item> items = SaleRequestHasher.normalize(List.of(
                new SaleItemRequest(2L, 1),
                new SaleItemRequest(1L, 3),
                new SaleItemRequest(2L, 4)));

        assertThat(items).containsExactly(new Item(1L, 3), new Item(2L, 5));
    }

    @Test
    @DisplayName("Pedidos equivalentes (mesmos itens em outra ordem) geram o mesmo hash")
    void equivalentRequestsProduceTheSameHash() {
        List<Item> first = SaleRequestHasher.normalize(List.of(new SaleItemRequest(1L, 2), new SaleItemRequest(2L, 1)));
        List<Item> second = SaleRequestHasher.normalize(List.of(new SaleItemRequest(2L, 1), new SaleItemRequest(1L, 2)));

        assertThat(SaleRequestHasher.hash("maria", first)).isEqualTo(SaleRequestHasher.hash("maria", second));
    }

    @Test
    @DisplayName("Quantidade diferente gera hash diferente")
    void differentQuantityProducesDifferentHash() {
        List<Item> one = SaleRequestHasher.normalize(List.of(new SaleItemRequest(1L, 2)));
        List<Item> other = SaleRequestHasher.normalize(List.of(new SaleItemRequest(1L, 3)));

        assertThat(SaleRequestHasher.hash("maria", one)).isNotEqualTo(SaleRequestHasher.hash("maria", other));
    }

    @Test
    @DisplayName("Usuário diferente gera hash diferente, mesmo com itens iguais")
    void differentUserProducesDifferentHash() {
        List<Item> items = SaleRequestHasher.normalize(List.of(new SaleItemRequest(1L, 2)));

        assertThat(SaleRequestHasher.hash("maria", items)).isNotEqualTo(SaleRequestHasher.hash("joao", items));
    }

    @Test
    @DisplayName("O hash é um SHA-256 em hexadecimal (64 caracteres)")
    void hashIsSha256Hex() {
        List<Item> items = SaleRequestHasher.normalize(List.of(new SaleItemRequest(1L, 2)));

        assertThat(SaleRequestHasher.hash("maria", items)).hasSize(64).matches("[0-9a-f]{64}");
    }
}
