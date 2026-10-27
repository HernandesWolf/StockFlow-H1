package com.stockflow.domain;

public enum Role {
    /** Cadastra produtos, ajusta estoque, cancela vendas, vê relatórios e gerencia usuários. */
    ADMIN,
    /** Opera o dia a dia: consulta, registra entradas/saídas e vende. */
    OPERATOR
}
