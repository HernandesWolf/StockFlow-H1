package com.stockflow;

import com.stockflow.service.ReportService;
import com.stockflow.service.SaleResult;
import com.stockflow.web.dto.ProductResponse;
import com.stockflow.web.dto.RevenueReportResponse;
import com.stockflow.web.dto.StockValuationResponse;
import com.stockflow.web.dto.TopProductResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Como os testes compartilham o banco, os relatórios globais podem incluir dados de outros testes;
 * por isso as conferências exatas são feitas sobre os produtos criados neste teste.
 */
class ReportsIT extends AbstractIntegrationTest {

    @Autowired
    private ReportService reportService;

    @Test
    @DisplayName("Produtos mais vendidos e receita consideram apenas vendas concluídas")
    void reportsIgnoreCancelledSales() {
        Long productId = newProduct(20);
        saleService.create(newKey(), sale(productId, 3), "operador"); // 3 x 10,00 = 30,00
        SaleResult cancelled = saleService.create(newKey(), sale(productId, 5), "operador"); // 5 x 10,00 = 50,00
        saleService.cancel(cancelled.sale().id(), "Teste de relatório", "admin");

        List<TopProductResponse> top = reportService.topProducts(null, null, 100);
        TopProductResponse mine = top.stream()
                .filter(row -> row.productId().equals(productId))
                .findFirst()
                .orElseThrow();
        assertThat(mine.quantitySold()).isEqualTo(3L);
        assertThat(mine.revenue()).isEqualByComparingTo("30.00");

        RevenueReportResponse revenue = reportService.revenue(null, null);
        assertThat(revenue.salesCount()).isGreaterThanOrEqualTo(1);
        assertThat(revenue.revenue()).isGreaterThanOrEqualTo(new BigDecimal("30.00"));
        assertThat(revenue.grossProfit()).isEqualByComparingTo(revenue.revenue().subtract(revenue.cost()));
        assertThat(revenue.daily()).isNotEmpty();
    }

    @Test
    @DisplayName("Alerta de reposição lista produtos com estoque igual ou abaixo do mínimo")
    void reorderAlertsListLowStockProducts() {
        Long lowStock = newProduct(1);   // mínimo = 1, estoque = 1 -> alerta
        Long healthy = newProduct(50);   // estoque bem acima do mínimo -> sem alerta

        List<Long> alertIds = reportService.reorderAlerts().stream().map(ProductResponse::id).toList();

        assertThat(alertIds).contains(lowStock).doesNotContain(healthy);
    }

    @Test
    @DisplayName("Valor do estoque: venda potencial menos custo é o lucro bruto potencial")
    void stockValuationIsConsistent() {
        newProduct(10); // custo 4,00 e venda 10,00 -> garante pelo menos 40,00 de custo e 100,00 de venda

        StockValuationResponse valuation = reportService.stockValuation();

        assertThat(valuation.totalUnits()).isGreaterThanOrEqualTo(10);
        assertThat(valuation.totalCostValue()).isGreaterThanOrEqualTo(new BigDecimal("40.00"));
        assertThat(valuation.potentialGrossProfit())
                .isEqualByComparingTo(valuation.totalSaleValue().subtract(valuation.totalCostValue()));
    }

    @Test
    @DisplayName("Ranking de margem vem ordenado da maior para a menor margem percentual")
    void topMarginIsSortedDescending() {
        newProduct(1); // margem de 60%

        List<ProductResponse> ranking = reportService.topMargin(50);

        assertThat(ranking).isNotEmpty();
        for (int i = 1; i < ranking.size(); i++) {
            assertThat(ranking.get(i - 1).grossMarginPercent())
                    .isGreaterThanOrEqualTo(ranking.get(i).grossMarginPercent());
        }
    }
}
