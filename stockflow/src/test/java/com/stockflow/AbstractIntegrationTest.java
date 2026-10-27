package com.stockflow;

import com.stockflow.service.ProductService;
import com.stockflow.service.SaleService;
import com.stockflow.web.dto.CreateProductRequest;
import com.stockflow.web.dto.CreateSaleRequest;
import com.stockflow.web.dto.SaleItemRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base dos testes de integração: sobe UM PostgreSQL real em Docker (Testcontainers) para toda a
 * execução de testes, e roda a aplicação inteira (Flyway, JPA, segurança) contra ele.
 *
 * Os testes NÃO limpam o banco: cada um cria seus próprios produtos com SKU único, então
 * podem rodar em qualquer ordem sem interferir uns nos outros.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    // "Singleton container": iniciado uma única vez e reaproveitado por todas as classes de teste.
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Folga de conexões para os testes com muitas threads simultâneas.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "40");
    }

    @Autowired
    protected ProductService productService;

    @Autowired
    protected SaleService saleService;

    @Autowired
    protected JdbcTemplate jdbc;

    // ------------------------------------------------------------------
    // Helpers de dados
    // ------------------------------------------------------------------

    /** Cria um produto (custo 4,00 / venda 10,00 / mínimo 1) com o estoque inicial informado. */
    protected Long newProduct(int initialStock) {
        String sku = "T-" + UUID.randomUUID().toString().substring(0, 12);
        return productService.create(
                new CreateProductRequest(sku, "Produto de teste", null, null,
                        new BigDecimal("4.00"), new BigDecimal("10.00"), 1, initialStock),
                "test").id();
    }

    protected static CreateSaleRequest sale(Long productId, int quantity) {
        return new CreateSaleRequest(List.of(new SaleItemRequest(productId, quantity)));
    }

    protected static String newKey() {
        return UUID.randomUUID().toString();
    }

    // ------------------------------------------------------------------
    // Helpers de consulta direta ao banco (para conferir a "verdade" sem passar pela aplicação)
    // ------------------------------------------------------------------

    protected int stockOf(Long productId) {
        Integer stock = jdbc.queryForObject(
                "select stock_quantity from product where id = ?", Integer.class, productId);
        return stock == null ? 0 : stock;
    }

    protected int countSaleItems(Long productId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from sale_item si join sale s on s.id = si.sale_id "
                        + "where si.product_id = ? and s.status = 'COMPLETED'", Integer.class, productId);
        return count == null ? 0 : count;
    }

    /** Invariante central do sistema: o estoque atual é SEMPRE a soma das variações do livro-razão. */
    protected void assertLedgerMatchesStock(Long productId) {
        Integer ledger = jdbc.queryForObject(
                "select coalesce(sum(quantity), 0) from stock_movement where product_id = ?",
                Integer.class, productId);
        assertThat(ledger).as("soma dos lançamentos de estoque do produto %s", productId)
                .isEqualTo(stockOf(productId));
    }

    // ------------------------------------------------------------------
    // Execução concorrente
    // ------------------------------------------------------------------

    protected record Outcome<T>(T value, Throwable error) {
        boolean ok() {
            return error == null;
        }
    }

    /**
     * Dispara {@code threads} tarefas ao MESMO tempo (todas esperam um "sinal de largada")
     * e devolve o resultado ou o erro de cada uma.
     */
    protected <T> List<Outcome<T>> runConcurrently(int threads, IntFunction<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return new Outcome<T>(task.apply(index), null);
                    } catch (Throwable error) {
                        return new Outcome<T>(null, error);
                    }
                }));
            }
            ready.await();
            start.countDown();

            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
