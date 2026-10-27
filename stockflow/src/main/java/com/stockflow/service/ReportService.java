package com.stockflow.service;

import com.stockflow.domain.Money;
import com.stockflow.domain.SaleStatus;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.DailyRevenueRow;
import com.stockflow.repository.ProductRepository;
import com.stockflow.repository.SaleItemRepository;
import com.stockflow.repository.SaleRepository;
import com.stockflow.web.dto.ProductResponse;
import com.stockflow.web.dto.RevenueReportResponse;
import com.stockflow.web.dto.StockValuationResponse;
import com.stockflow.web.dto.TopProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final int MAX_PERIOD_DAYS = 366;
    private static final int DEFAULT_PERIOD_DAYS = 30;

    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final ProductRepository products;
    private final ZoneId zone;

    public ReportService(SaleRepository sales,
                         SaleItemRepository saleItems,
                         ProductRepository products,
                         @Value("${stockflow.report.zone}") String zone) {
        this.sales = sales;
        this.saleItems = saleItems;
        this.products = products;
        this.zone = ZoneId.of(zone);
    }

    /** Produtos mais vendidos (por unidades) no período. Sem datas: últimos 30 dias. */
    public List<TopProductResponse> topProducts(LocalDate from, LocalDate to, int limit) {
        Period period = resolve(from, to);
        return saleItems.findTopSelling(SaleStatus.COMPLETED, period.start(), period.endExclusive(),
                PageRequest.of(0, clampLimit(limit)));
    }

    /** Receita, custo e lucro bruto do período, com a série diária. Vendas canceladas não contam. */
    public RevenueReportResponse revenue(LocalDate from, LocalDate to) {
        Period period = resolve(from, to);
        List<RevenueReportResponse.DailyRevenue> daily = sales
                .dailyRevenue(zone.getId(), period.start(), period.endExclusive()).stream()
                .map(ReportService::toDaily)
                .toList();

        long salesCount = daily.stream().mapToLong(RevenueReportResponse.DailyRevenue::salesCount).sum();
        BigDecimal revenue = daily.stream()
                .map(RevenueReportResponse.DailyRevenue::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = saleItems.sumCost(SaleStatus.COMPLETED, period.start(), period.endExclusive());

        BigDecimal revenueMoney = Money.of(revenue);
        BigDecimal costMoney = cost == null ? Money.zero() : Money.of(cost);
        return new RevenueReportResponse(period.from(), period.to(), salesCount, revenueMoney, costMoney,
                revenueMoney.subtract(costMoney), daily);
    }

    public StockValuationResponse stockValuation() {
        return products.stockValuation();
    }

    /** Produtos ativos com maior margem bruta percentual. */
    public List<ProductResponse> topMargin(int limit) {
        return products.findTopMargin(PageRequest.of(0, clampLimit(limit))).stream()
                .map(ProductResponse::from)
                .toList();
    }

    /** Produtos ativos com estoque no mínimo (ou abaixo): hora de repor. */
    public List<ProductResponse> reorderAlerts() {
        return products.findReorderAlerts().stream().map(ProductResponse::from).toList();
    }

    // ------------------------------------------------------------------

    private record Period(LocalDate from, LocalDate to, Instant start, Instant endExclusive) {
    }

    private Period resolve(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(zone);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_PERIOD_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BadRequestException("A data inicial não pode ser posterior à data final.");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_PERIOD_DAYS) {
            throw new BadRequestException("O período máximo do relatório é de " + MAX_PERIOD_DAYS + " dias.");
        }
        return new Period(start, end,
                start.atStartOfDay(zone).toInstant(),
                end.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private static RevenueReportResponse.DailyRevenue toDaily(DailyRevenueRow row) {
        return new RevenueReportResponse.DailyRevenue(
                LocalDate.parse(row.getBucket()),
                row.getSales() == null ? 0L : row.getSales(),
                row.getRevenue() == null ? Money.zero() : Money.of(row.getRevenue()));
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
