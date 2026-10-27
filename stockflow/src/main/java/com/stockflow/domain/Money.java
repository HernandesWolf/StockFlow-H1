package com.stockflow.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Utilitário para manter todos os valores monetários com 2 casas decimais. */
public final class Money {

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
