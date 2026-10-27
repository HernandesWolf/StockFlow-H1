package com.stockflow.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Receita, custo e lucro bruto de um período (somente vendas concluídas). */
public record RevenueReportResponse(
        LocalDate from,
        LocalDate to,
        long salesCount,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal grossProfit,
        List<DailyRevenue> daily) {

    public record DailyRevenue(LocalDate date, long salesCount, BigDecimal revenue) {
    }
}
