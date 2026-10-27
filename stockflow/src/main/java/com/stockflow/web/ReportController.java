package com.stockflow.web;

import com.stockflow.service.ReportService;
import com.stockflow.web.dto.ProductResponse;
import com.stockflow.web.dto.RevenueReportResponse;
import com.stockflow.web.dto.StockValuationResponse;
import com.stockflow.web.dto.TopProductResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Relatórios (somente ADMIN, regra no SecurityConfig). Datas no formato yyyy-MM-dd. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/top-products")
    public List<TopProductResponse> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.topProducts(from, to, limit);
    }

    @GetMapping("/revenue")
    public RevenueReportResponse revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.revenue(from, to);
    }

    @GetMapping("/stock-valuation")
    public StockValuationResponse stockValuation() {
        return reportService.stockValuation();
    }

    @GetMapping("/top-margin")
    public List<ProductResponse> topMargin(@RequestParam(defaultValue = "10") int limit) {
        return reportService.topMargin(limit);
    }

    @GetMapping("/reorder-alerts")
    public List<ProductResponse> reorderAlerts() {
        return reportService.reorderAlerts();
    }
}
