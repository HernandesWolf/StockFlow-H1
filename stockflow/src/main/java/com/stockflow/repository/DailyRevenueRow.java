package com.stockflow.repository;

import java.math.BigDecimal;

/** Projeção do resultado da consulta nativa de receita diária. */
public interface DailyRevenueRow {

    /** Data no formato yyyy-MM-dd, já no fuso do relatório. */
    String getBucket();

    Long getSales();

    BigDecimal getRevenue();
}
